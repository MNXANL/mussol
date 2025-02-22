/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.fieldofplay;

import static app.owlcms.fieldofplay.FOPState.BREAK;
import static app.owlcms.fieldofplay.FOPState.CURRENT_ATHLETE_DISPLAYED;
import static app.owlcms.fieldofplay.FOPState.DECISION_VISIBLE;
import static app.owlcms.fieldofplay.FOPState.DOWN_SIGNAL_VISIBLE;
import static app.owlcms.fieldofplay.FOPState.INACTIVE;
import static app.owlcms.fieldofplay.FOPState.TIME_RUNNING;
import static app.owlcms.fieldofplay.FOPState.TIME_STOPPED;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import app.owlcms.data.agegroup.AgeGroup;
import app.owlcms.data.agegroup.AgeGroupRepository;
import app.owlcms.data.athlete.Athlete;
import app.owlcms.data.athlete.AthleteRepository;
import app.owlcms.data.athleteSort.AthleteSorter;
import app.owlcms.data.athleteSort.AthleteSorter.Ranking;
import app.owlcms.data.category.Category;
import app.owlcms.data.category.Participation;
import app.owlcms.data.group.Group;
import app.owlcms.data.jpa.JPAService;
import app.owlcms.data.platform.Platform;
import app.owlcms.fieldofplay.FOPEvent.BarbellOrPlatesChanged;
import app.owlcms.fieldofplay.FOPEvent.BreakPaused;
import app.owlcms.fieldofplay.FOPEvent.BreakStarted;
import app.owlcms.fieldofplay.FOPEvent.DecisionFullUpdate;
import app.owlcms.fieldofplay.FOPEvent.DecisionReset;
import app.owlcms.fieldofplay.FOPEvent.DecisionUpdate;
import app.owlcms.fieldofplay.FOPEvent.DownSignal;
import app.owlcms.fieldofplay.FOPEvent.ExplicitDecision;
import app.owlcms.fieldofplay.FOPEvent.ForceTime;
import app.owlcms.fieldofplay.FOPEvent.JuryDecision;
import app.owlcms.fieldofplay.FOPEvent.StartLifting;
import app.owlcms.fieldofplay.FOPEvent.SwitchGroup;
import app.owlcms.fieldofplay.FOPEvent.TimeOver;
import app.owlcms.fieldofplay.FOPEvent.TimeStarted;
import app.owlcms.fieldofplay.FOPEvent.TimeStopped;
import app.owlcms.fieldofplay.FOPEvent.WeightChange;
import app.owlcms.i18n.Translator;
import app.owlcms.init.OwlcmsSession;
import app.owlcms.sound.Sound;
import app.owlcms.sound.Tone;
import app.owlcms.ui.shared.BreakManagement.CountdownType;
import app.owlcms.uievents.BreakType;
import app.owlcms.uievents.EventForwarder;
import app.owlcms.uievents.JuryDeliberationEventType;
import app.owlcms.uievents.UIEvent;
import app.owlcms.uievents.UIEvent.JuryNotification;
import app.owlcms.utils.LoggerUtils;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * This class describes one field of play at runtime.
 *
 * It encapsulates the in-memory data structures used to describe the state of the competition and links them to the
 * database descriptions of the group and platform.
 *
 * The main method is {@link #handleFOPEvent(FOPEvent)} which implements a state automaton and processes events received
 * on the event bus.
 *
 * @author owlcms
 */
public class FieldOfPlay {

    private class DelayTimer {
        private final Timer t = new Timer();

        public TimerTask schedule(final Runnable r, long delay) {
            if (isTestingMode()) {
                r.run();
                return null;
            } else {
                final TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        r.run();
                    }
                };
                t.schedule(task, delay);
                return task;
            }
        }
    }

    private static final int REVERSAL_DELAY = 3000;

    private static final long DECISION_VISIBLE_DURATION = 3500;

    final private Logger logger = (Logger) LoggerFactory.getLogger(FieldOfPlay.class);

    final private Logger uiEventLogger = (Logger) LoggerFactory.getLogger("UI" + logger.getName());

    {
        uiEventLogger.setLevel(Level.INFO);
    }
    /**
     * the clock owner is the last athlete for whom the clock has actually started.
     */
    private Athlete clockOwner;
    private Athlete curAthlete;
    private EventBus fopEventBus = null;
    private EventBus uiEventBus = null;
    private EventBus postBus = null;
    private Group group = null;
    private String name;
    private Platform platform = null;
    private Athlete previousAthlete;
    private FOPState state;
    private IProxyTimer athleteTimer;
    private IProxyTimer breakTimer;
    private BreakType breakType;
    private List<Athlete> liftingOrder;
    private List<Athlete> displayOrder;
    private int curWeight;
    private Tone downSignal;
    private boolean initialWarningEmitted;
    private boolean finalWarningEmitted;
    private boolean timeoutEmitted;
    private boolean downEmitted;
    private Boolean[] refereeDecision;
    private boolean decisionDisplayScheduled = false;

    private Integer[] refereeTime;
    private Boolean goodLift;

    private boolean testingMode;

    private CountdownType countdownType;

    private boolean cjStarted;

    private Integer prevHash;

    private boolean refereeForcedDecision;

    private Integer weightAtLastStart;

    private int clockOwnerInitialTimeAllowed;

    private int liftsDoneAtLastStart;

    private List<Athlete> leaders;

    private LinkedHashMap<String, Participation> ageGroupMap = new LinkedHashMap<>();

    /**
     * Instantiates a new field of play state. When using this constructor {@link #init(List, IProxyTimer)} must later
     * be used to provide the athletes and set the athleteTimer
     *
     * @param group     the group (to get details such as name, and to reload athletes)
     * @param platform2 the platform (to get details such as name)
     */
    public FieldOfPlay(Group group, Platform platform2) {
        this.name = platform2.getName();
        this.fopEventBus = new EventBus("FOP-" + name);
        this.postBus = new EventBus("POST-" + name);

        // this.uiEventBus = new EventBus("UI-" + name);
        this.uiEventBus = new AsyncEventBus(Executors.newCachedThreadPool());

        this.athleteTimer = null;
        this.breakTimer = new ProxyBreakTimer(this);
        this.setPlatform(platform2);

        this.fopEventBus.register(this);
        EventForwarder.listenToFOP(this);
    }

    /**
     * Instantiates a new field of play state. This constructor is only used for testing using mock timers.
     *
     * @param athletes the athletes
     * @param timer1   the athleteTimer
     */
    public FieldOfPlay(List<Athlete> athletes, IProxyTimer timer1, IProxyTimer breakTimer1, boolean testingMode) {
        this.name = "test";
        this.fopEventBus = new EventBus("FOP-" + this.name);
        this.uiEventBus = new EventBus("UI-" + this.name);
        this.postBus = new EventBus("POST-" + name);
        this.setTestingMode(testingMode);
        this.group = new Group();
        init(athletes, timer1, breakTimer1, true);

        this.fopEventBus.register(this);
    }

    public void beforeTest() {
        setWeightAtLastStart(0);
        startLifting(null, null);
        return;
    }

    /**
     * @return how many lifts done so far in the group.
     */
    public int countLiftsDone() {
        int liftsDone = AthleteSorter.countLiftsDone(getDisplayOrder());
        return liftsDone;
    }

    public void emitDown(FOPEvent e) {
        logger.debug("{}Emitting down {}", getLoggingName(), LoggerUtils.whereFrom(2));
        getAthleteTimer().stop(); // paranoia
        this.setPreviousAthlete(getCurAthlete()); // would be safer to use past lifting order
        setClockOwner(null); // athlete has lifted, time does not keep running for them
        setClockOwnerInitialTimeAllowed(0);
        uiShowDownSignalOnSlaveDisplays(e.origin);
        setState(DOWN_SIGNAL_VISIBLE);
    }

    public void emitFinalWarning() {
        if (!isFinalWarningEmitted()) {
            logger.info("{}Final Warning", getLoggingName());
            if (isEmitSoundsOnServer()) {
                // instead of finalWarning2.wav sounds too much like down
                new Sound(getSoundMixer(), "initialWarning2.wav").emit();
            }
            setFinalWarningEmitted(true);
        }
    }

    public void emitInitialWarning() {
        if (!isInitialWarningEmitted()) {
            logger.info("{}Initial Warning", getLoggingName());
            if (isEmitSoundsOnServer()) {
                new Sound(getSoundMixer(), "initialWarning2.wav").emit();
            }
            setInitialWarningEmitted(true);
        }
    }

    public void emitTimeOver() {
        if (!isTimeoutEmitted()) {
            logger.info("{}Time Over", getLoggingName());
            if (isEmitSoundsOnServer()) {
                new Sound(getSoundMixer(), "timeOver2.wav").emit();
            }
            setTimeoutEmitted(true);
        }
    }

    public LinkedHashMap<String, Participation> getAgeGroupMap() {
        return ageGroupMap;
    }

    /**
     * @return the server-side athleteTimer that tracks the time used
     */
    public IProxyTimer getAthleteTimer() {
        return this.athleteTimer;
    }

    public IBreakTimer getBreakTimer() {
        // if (!(this.breakTimer.getClass().isAssignableFrom(ProxyBreakTimer.class)))
        // throw new RuntimeException("wrong athleteTimer setup");
        return (IBreakTimer) this.breakTimer;
    }

    public BreakType getBreakType() {
        return breakType;
    }

    public Athlete getClockOwner() {
        return clockOwner;
    }

    /**
     * @return 0 if clock has not been started, 120000 or 60000 depending on time allowed when clock is started
     */
    public int getClockOwnerInitialTimeAllowed() {
        return clockOwnerInitialTimeAllowed;
    }

    public CountdownType getCountdownType() {
        return countdownType;
    }

    /**
     * @return the current athlete (to be called, or currently lifting)
     */
    public Athlete getCurAthlete() {
        return curAthlete;
    }

    public List<Athlete> getDisplayOrder() {
        return displayOrder;
    }

    /**
     * @return the fopEventBus
     */
    public EventBus getFopEventBus() {
        return fopEventBus;
    }

    /**
     * @return the group
     */
    public Group getGroup() {
        return group;
    }

    /**
     * @return the leaders
     */
    public List<Athlete> getLeaders() {
        return leaders;
    }

    /**
     * @return the lifters
     */
    public List<Athlete> getLiftingOrder() {
        return liftingOrder;
    }

    /**
     * @return the liftsDoneAtLastStart
     */
    public int getLiftsDoneAtLastStart() {
        return liftsDoneAtLastStart;
    }

    /**
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * @return the name
     */
    public String getLoggingName() {
        return "FOP " + name + "    ";
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the platform
     */
    public Platform getPlatform() {
        return platform;
    }

    public EventBus getPostEventBus() {
        return postBus;
    }

    /**
     * @return the previous athlete to have lifted (can be the same as current)
     */
    public Athlete getPreviousAthlete() {
        return previousAthlete;
    }

    /**
     * @return the current state
     */
    public FOPState getState() {
        return state;
    }

    /**
     * @return the time allowed for the next athlete.
     */
    public int getTimeAllowed() {
        Athlete a = getCurAthlete();
        int timeAllowed;
        int initialTime;

        Athlete owner = getClockOwner();
        if (owner != null && owner.equals(a)) {
            // the clock was started for us. we own the clock, clock is already set to what time was
            // left
            timeAllowed = getAthleteTimer().getTimeRemainingAtLastStop();
        } else if (getPreviousAthlete() != null && getPreviousAthlete().equals(a)) {
            resetDecisions();
            if (owner != null || a.getAttemptNumber() == 1) {
                // clock has started for someone else, one minute
                // first C&J, one minute (doesn't matter who lifted last during snatch)
                timeAllowed = 60000;
            } else {
                timeAllowed = 120000;
            }
        } else {
            resetDecisions();
            timeAllowed = 60000;
        }

        initialTime = getClockOwnerInitialTimeAllowed();
        logger.debug("{}curAthlete = {}, clock owner = {}, timeRemaining = {}, initialTime = {}", getLoggingName(), a,
                owner, timeAllowed, initialTime);
        return timeAllowed;
    }

    /**
     * @return the bus on which we post commands for the listening browser pages.
     */
    public EventBus getUiEventBus() {
        return uiEventBus;
    }

    public Integer getWeightAtLastStart() {
        return weightAtLastStart;
    }

    /**
     * Handle field of play events.
     *
     * FOP (Field of Play) events inform us of what is happening (e.g. athleteTimer started by timekeeper, decision
     * given by official, etc.) The current state determines what we do with the event. Typically, we update the state
     * of the field of play (e.g. time is now running) and we issue commands to the listening user interfaces (e.g.
     * start or stop time being displayed, show the decision, etc.)
     *
     * There is a fopEventBus for each active field of play. A given user interface will issue a FOP event on our
     * fopEventBus, this method reacts to the event by updating state, and we issue the resulting user interface
     * commands on the @link uiEventBus.
     *
     * One exception is timers: the task to send UI events to start stop/start/manage timers is delegated to
     * implementers of IProxyTimer; these classes remember the time and broadcast to all listening timers.
     *
     * @param e the event
     */
    @Subscribe
    public void handleFOPEvent(FOPEvent e) {
        int newHash = e.hashCode();
        if (prevHash != null && newHash == prevHash) {
            prevHash = newHash;
            logger.debug("{}state {}, DUPLICATE event received {} {}", getLoggingName(), this.getState(),
                    e.getClass().getSimpleName(), e);
            return;
        } else {
            logger.info("{}state {}, event received {}", getLoggingName(), this.getState(),
                    e.getClass().getSimpleName(),
                    e);
            prevHash = newHash;
        }
        // it is always possible to explicitly interrupt competition (break between the
        // two lifts, technical incident, etc.)
        if (e instanceof BreakStarted) {
            transitionToBreak((BreakStarted) e);
            return;
        } else if (e instanceof BreakPaused) {
            // logger.debug("break paused {}", LoggerUtils. stackTrace());
        } else if (e instanceof StartLifting) {
            transitionToLifting(e, getGroup(), true);
        } else if (e instanceof BarbellOrPlatesChanged) {
            uiShowPlates((BarbellOrPlatesChanged) e);
            return;
        } else if (e instanceof SwitchGroup) {
            Group oldGroup = this.getGroup();
            SwitchGroup switchGroup = (SwitchGroup) e;
            Group newGroup = switchGroup.getGroup();

            boolean inBreak = state == BREAK || state == INACTIVE;
            if (ObjectUtils.equals(oldGroup, newGroup)) {
                loadGroup(newGroup, this, true);
                if (inBreak) {
                    pushOut(new UIEvent.SwitchGroup(this.getGroup(), this.getState(), this.getCurAthlete(),
                            e.getOrigin()));
                } else {
                    // start lifting.
                    transitionToLifting(e, newGroup, inBreak);
                }
            } else {
                if (!inBreak) {
                    setState(INACTIVE);
                    athleteTimer.stop();
                } else if (state == BREAK && breakType == BreakType.GROUP_DONE) {
                    setState(INACTIVE);
                }
                loadGroup(newGroup, this, true);
            }
            return;
        }

        switch (this.getState()) {

        case INACTIVE:
            if (e instanceof BreakStarted) {
                transitionToBreak((BreakStarted) e);
            } else if (e instanceof TimeStarted) {
                transitionToTimeRunning();
            } else if (e instanceof WeightChange) {
                doWeightChange((WeightChange) e);
            } else {
                unexpectedEventInState(e, INACTIVE);
            }
            break;

        case BREAK:
            if (e instanceof StartLifting) {
                transitionToLifting(e, getGroup(), true);
            } else if (e instanceof BreakPaused) {
                BreakPaused bpe = (BreakPaused) e;
                getBreakTimer().stop();
                getBreakTimer().setTimeRemaining(bpe.getTimeRemaining());
                pushOut(new UIEvent.BreakPaused(
                        bpe.getTimeRemaining(),
                        e.getOrigin(),
                        false,
                        this.getBreakType(),
                        this.getCountdownType()));
            } else if (e instanceof BreakStarted) {
                transitionToBreak((BreakStarted) e);
            } else if (e instanceof WeightChange) {
                doWeightChange((WeightChange) e);
            } else if (e instanceof JuryDecision) {
                doJuryDecision((JuryDecision) e);
            } else {
                unexpectedEventInState(e, BREAK);
            }
            break;

        case CURRENT_ATHLETE_DISPLAYED:
            if (e instanceof TimeStarted) {
                transitionToTimeRunning();
            } else if (e instanceof WeightChange) {
                doWeightChange((WeightChange) e);
            } else if (e instanceof ForceTime) {
                // need to set time
                getAthleteTimer().setTimeRemaining(((ForceTime) e).timeAllowed);
                setState(CURRENT_ATHLETE_DISPLAYED);
            } else if (e instanceof StartLifting) {
                // announcer can set break manually
                setState(CURRENT_ATHLETE_DISPLAYED);
            } else {
                unexpectedEventInState(e, CURRENT_ATHLETE_DISPLAYED);
            }
            break;

        case TIME_RUNNING:
            if (e instanceof DownSignal) {
                emitDown(e);
            } else if (e instanceof TimeStopped) {
                // athlete lifted the bar
                getAthleteTimer().stop();
                setState(TIME_STOPPED);
            } else if (e instanceof DecisionFullUpdate) {
                // decision board/attempt board sends bulk update
                updateRefereeDecisions((DecisionFullUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof DecisionUpdate) {
                updateRefereeDecisions((DecisionUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof WeightChange) {
                doWeightChange((WeightChange) e);
            } else if (e instanceof ExplicitDecision) {
                simulateDecision((ExplicitDecision) e);
            } else if (e instanceof TimeOver) {
                // athleteTimer got down to 0
                // getTimer() signals this, nothing else required for athleteTimer
                // rule says referees must give reds
                setState(TIME_STOPPED);
            } else {
                unexpectedEventInState(e, TIME_RUNNING);
            }
            break;

        case TIME_STOPPED:
            if (e instanceof DownSignal) {
                // ignore -- now processed via processRefereeDecisions()
                // 2 referees have given same decision
                // emitDown(e);
            } else if (e instanceof DecisionFullUpdate) {
                // decision coming from decision display or attempt board
                updateRefereeDecisions((DecisionFullUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof DecisionUpdate) {
                updateRefereeDecisions((DecisionUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof TimeStarted) {
                getAthleteTimer().start();
                if (!getCurAthlete().equals(getClockOwner())) {
                    setClockOwner(getCurAthlete());
                    setClockOwnerInitialTimeAllowed(getTimeAllowed());
                }
                prepareDownSignal();
                setWeightAtLastStart();

                // we do not reset decisions or "emitted" flags
                setState(TIME_RUNNING);
            } else if (e instanceof WeightChange) {
                doWeightChange((WeightChange) e);
            } else if (e instanceof ExplicitDecision) {
                simulateDecision((ExplicitDecision) e);
            } else if (e instanceof ForceTime) {
                getAthleteTimer().setTimeRemaining(((ForceTime) e).timeAllowed);
                setState(CURRENT_ATHLETE_DISPLAYED);
            } else if (e instanceof TimeStopped) {
                // ignore duplicate time stopped
            } else if (e instanceof TimeOver) {
                // ignore, already dealt by timer
            } else if (e instanceof StartLifting) {
                // nothing to do, end of break when clock was already started
            } else {
                unexpectedEventInState(e, TIME_STOPPED);
            }
            break;

        case DOWN_SIGNAL_VISIBLE:
            if (e instanceof ExplicitDecision) {
                simulateDecision((ExplicitDecision) e);
            } else if (e instanceof DecisionFullUpdate) {
                // decision coming from decision display or attempt board
                updateRefereeDecisions((DecisionFullUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof DecisionUpdate) {
                updateRefereeDecisions((DecisionUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof WeightChange) {
                weightChangeDoNotDisturb((WeightChange) e);
                setState(DOWN_SIGNAL_VISIBLE);
            } else {
                unexpectedEventInState(e, DOWN_SIGNAL_VISIBLE);
            }
            break;

        case DECISION_VISIBLE:
            if (e instanceof ExplicitDecision) {
                simulateDecision((ExplicitDecision) e);
                // showExplicitDecision(((ExplicitDecision) e), e.origin);
            } else if (e instanceof DecisionFullUpdate) {
                // decision coming from decision display or attempt board
                updateRefereeDecisions((DecisionFullUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof DecisionUpdate) {
                updateRefereeDecisions((DecisionUpdate) e);
                uiShowUpdateOnJuryScreen();
            } else if (e instanceof WeightChange) {
                weightChangeDoNotDisturb((WeightChange) e);
                setState(DECISION_VISIBLE);
            } else if (e instanceof DecisionReset) {
                logger.debug("{}resetting decisions", getLoggingName());
                pushOut(new UIEvent.DecisionReset(getCurAthlete(), e.origin));
                setClockOwner(null);
                displayOrBreakIfDone(e);
            } else {
                unexpectedEventInState(e, DECISION_VISIBLE);
            }
            break;
        }
    }

    private void doJuryDecision(JuryDecision e) {
        Athlete a = e.getAthlete();
        Integer actualLift = a.getActualLift(a.getAttemptsDone());
        if (actualLift != null) {
            Integer curValue = Math.abs(actualLift);
            a.doLift(a.getAttemptsDone(), e.success ? Integer.toString(curValue) : Integer.toString(-curValue));
            AthleteRepository.save(a);
            JuryNotification event = new UIEvent.JuryNotification(a, e.getOrigin(),
                    e.success ? JuryDeliberationEventType.GOOD_LIFT : JuryDeliberationEventType.BAD_LIFT,
                    e.success && actualLift <= 0 || !e.success && actualLift > 0);
            OwlcmsSession.getFop().getUiEventBus().post(event);
            recomputeLiftingOrder();
        }
    }

    public void init(List<Athlete> athletes, IProxyTimer timer, IProxyTimer breakTimer, boolean alreadyLoaded) {
        //logger.debug("start of init state={} \\n{}", state, LoggerUtils. stackTrace());
        this.athleteTimer = timer;
        this.breakTimer = breakTimer;
        this.setCurAthlete(null);
        this.setClockOwner(null);
        this.setClockOwnerInitialTimeAllowed(0);
        this.setPreviousAthlete(null);
        this.setLiftingOrder(athletes);
        List<AgeGroup> allAgeGroups = AgeGroupRepository.findAgeGroups(getGroup());
        this.ageGroupMap = new LinkedHashMap<>();
        for (AgeGroup ag : allAgeGroups) {
            ageGroupMap.put(ag.getCode(), null);
        }

        if (athletes != null && athletes.size() > 0) {
            recomputeLiftingOrder();
        }
        if (getGroup() != null) {
        }
        if (state == null) {
            this.setState(INACTIVE);
        }

        // force a wake up on user interfaces
        if (!alreadyLoaded) {
            logger.info("{}group {} athletes={}", getLoggingName(), getGroup(), athletes.size());
            pushOut(new UIEvent.SwitchGroup(getGroup(), getState(), getCurAthlete(), this));
        }
        logger.debug("end of init state=" + state);
    }

    public boolean isCjStarted() {
        return cjStarted;
    }

    public boolean isEmitSoundsOnServer() {
        boolean b = getSoundMixer() != null;
        logger.trace("emit sound on server = {}", b);
        return b;
    }

    public boolean isTestingMode() {
        return testingMode;
    }

    public synchronized boolean isTimeoutEmitted() {
        return timeoutEmitted;
    }

    /**
     * all grids get their athletes from this method.
     *
     * @param group
     * @param origin
     * @param forceLoad reload from database even if current group
     */
    public void loadGroup(Group group, Object origin, boolean forceLoad) {
        String thisGroupName = this.getGroup() != null ? this.getGroup().getName() : null;
        String loadGroupName = group != null ? group.getName() : null;
        boolean alreadyLoaded = thisGroupName == loadGroupName;
        if (loadGroupName != null && alreadyLoaded && !forceLoad) {
            // already loaded
//            logger.trace("{}group {} already loaded", getLoggingName(), loadGroupName);
            return;
        }
        this.setGroup(group);
        if (group != null) {
//            logger.trace("{}current group {} loading data for group {} [{} {} {} {}]",
//                    getLoggingName(),
//                    thisGroupName,
//                    loadGroupName,
//                    alreadyLoaded,
//                    forceLoad,
//                    origin.getClass().getSimpleName(),
//                    LoggerUtils.whereFrom());
            List<Athlete> groupAthletes = AthleteRepository.findAllByGroupAndWeighIn(group, true);
            if (groupAthletes.stream().map(Athlete::getStartNumber).anyMatch(sn -> sn == 0)) {
                logger./**/warn("start numbers were not assigned correctly");
                AthleteRepository.assignStartNumbers(group);
                groupAthletes = AthleteRepository.findAllByGroupAndWeighIn(group, true);
            }
            init(groupAthletes, athleteTimer, breakTimer, alreadyLoaded);
        } else {
            init(new ArrayList<Athlete>(), athleteTimer, breakTimer, alreadyLoaded);
        }
    }

    public void pushOut(UIEvent event) {
        getUiEventBus().post(event);
        getPostEventBus().post(event);
    }

    public synchronized void recomputeLiftingOrder() {
        recomputeLiftingOrder(true);
    }

    /**
     * Recompute lifting order, category ranks, and leaders for current category. Sets rankings including previous
     * lifters for all categories in the current group.
     */
    public void recomputeOrderAndRanks() {
        Group g = getGroup();
        // we update the ranks of affected athletes in the database
        JPAService.runInTransaction(em -> {
            List<Athlete> l = AthleteSorter.assignCategoryRanks(g);
            for (Athlete a : l) {
                em.merge(a);
            }
            em.flush();
            return null;
        });
        List<Athlete> rankedAthletes = AthleteRepository.findAthletesForGlobalRanking(g);
        if (rankedAthletes == null) {
            setDisplayOrder(null);
            setCurAthlete(null);
            return;
        }
        List<Athlete> currentGroupAthletes = AthleteSorter.displayOrderCopy(rankedAthletes).stream()
                .filter(a -> a.getGroup() != null ? a.getGroup().equals(g) : false)
                .collect(Collectors.toList());
        setDisplayOrder(currentGroupAthletes);

        setLiftingOrder(AthleteSorter.liftingOrderCopy(currentGroupAthletes));

        List<Athlete> liftingOrder2 = getLiftingOrder();
        if (logger.isEnabledFor(Level.TRACE)) {
            for (Athlete a : getLiftingOrder()) {
                Participation p = a.getMainRankings();
                logger./**/warn("{}{} {} {} {} {} {}", getLoggingName(), a, p.getCategory(), p.getSnatchRank(),
                        p.getCleanJerkRank(),
                        p.getTotalRank(), a.isForcedAsCurrent());
            }
        }
        setCurAthlete(liftingOrder2 != null && liftingOrder2.size() > 0 ? liftingOrder2.get(0) : null);
        recomputeCurrentLeaders(rankedAthletes);
    }

    /**
     * Sets the athleteTimer.
     *
     * @param athleteTimer the new athleteTimer
     */
    public void setAthleteTimer(IProxyTimer timer) {
        this.athleteTimer = timer;
    }

    public void setBreakType(BreakType breakType) {
        this.breakType = breakType;
    }

    public void setCjStarted(boolean cjStarted) {
        this.cjStarted = cjStarted;
    }

    public void setCountdownType(CountdownType countdownType) {
        this.countdownType = countdownType;
    }

    /**
     * Sets the group.
     *
     * @param group the group to set
     */
    public void setGroup(Group group) {
        this.group = group;
    }

    /**
     * @param leaders the leaders to set
     */
    public void setLeaders(List<Athlete> leaders) {
        this.leaders = leaders;
    }

    /**
     * @param liftsDoneAtLastStart the liftsDoneAtLastStart to set
     */
    public void setLiftsDoneAtLastStart(int liftsDoneAtLastStart) {
        this.liftsDoneAtLastStart = liftsDoneAtLastStart;
    }

    /**
     * Sets the name.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the platform.
     *
     * @param platform the platform to set
     */
    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    /**
     * @param testingMode true if we don't want wait delays during testing.
     */
    public void setTestingMode(boolean testingMode) {
        this.testingMode = testingMode;
    }

    public void setWeightAtLastStart(Integer nextAttemptRequestedWeight) {
        weightAtLastStart = nextAttemptRequestedWeight;
        setLiftsDoneAtLastStart(((getCurAthlete() != null) ? getCurAthlete().getAttemptsDone() : 0));
    }

    /**
     * Switch group.
     *
     * @param group the group
     */
    public void startLifting(Group group, Object origin) {
        //logger.debug("startLifting {}", LoggerUtils. stackTrace());
        loadGroup(group, origin, true);
        logger.trace("{} start lifting for group {} origin={}", this.getLoggingName(),
                (group != null ? group.getName() : group), origin);
        getFopEventBus().post(new StartLifting(origin));
    }

    public void uiDisplayCurrentAthleteAndTime(boolean currentDisplayAffected, FOPEvent e, boolean displayToggle) {
        Integer clock = getAthleteTimer().getTimeRemaining();

        curWeight = 0;
        if (getCurAthlete() != null) {
            curWeight = getCurAthlete().getNextAttemptRequestedWeight();
        }
        // if only one athlete, no next athlete
        Athlete nextAthlete = getLiftingOrder().size() > 1 ? getLiftingOrder().get(1) : null;

        Athlete changingAthlete = null;
        if (e instanceof WeightChange) {
            changingAthlete = e.getAthlete();
        }
        boolean inBreak = false;
        if (state == FOPState.BREAK) {
            inBreak = ((breakTimer != null && breakTimer.isRunning()));
        }
        logger.trace("uiDisplayCurrentAthleteAndTime {} {} {} {} {}", getCurAthlete(), inBreak, getPreviousAthlete(),
                nextAthlete, currentDisplayAffected);
        pushOut(new UIEvent.LiftingOrderUpdated(getCurAthlete(), nextAthlete, getPreviousAthlete(), changingAthlete,
                getLiftingOrder(), getDisplayOrder(), clock, currentDisplayAffected, displayToggle, e.getOrigin(),
                inBreak));

        // cur athlete can be null during some tests.
        int attempts = getCurAthlete() == null ? 0 : getCurAthlete().getAttemptedLifts() + 1;
        String shortName = getCurAthlete() == null ? "" : getCurAthlete().getShortName();
        logger.info("{}current athlete = {} attempt = {}, requested = {}, clock={} initialTime={}",
                getLoggingName(), shortName, attempts, curWeight,
                clock,
                getClockOwnerInitialTimeAllowed());
        if (attempts > 6) {
            pushOutDone();
        }
    }

    /**
     * Sets the state.
     *
     * @param state the new state
     */
    void setState(FOPState state) {
        logger.debug("{}entering {} {}", getLoggingName(), state, LoggerUtils.whereFrom());
        // if (state == INACTIVE) {
        // logger.debug("entering inactive {}",LoggerUtils. stackTrace());
        // }
        if (state == CURRENT_ATHLETE_DISPLAYED) {
            Athlete a = getCurAthlete();
            if (group != null) {
                group.setDone(a == null || a.getAttemptsDone() >= 6);
            }
        } else if (state == BREAK && group != null) {
            group.setDone(breakType == BreakType.GROUP_DONE);
        }
        this.state = state;
    }

    public void broadcast(String string) {
        getUiEventBus().post(new UIEvent.Broadcast(string, this));
    }

    private void displayOrBreakIfDone(FOPEvent e) {
        if (getCurAthlete() != null && getCurAthlete().getAttemptsDone() < 6) {
            uiDisplayCurrentAthleteAndTime(true, e, false);
            setState(CURRENT_ATHLETE_DISPLAYED);
            group.setDone(false);
        } else {
            // special kind of break that allows moving back in case of jury reversal
            setBreakType(BreakType.GROUP_DONE);
            setState(BREAK);
            group.setDone(true);
            pushOutDone();
        }
    }

    /**
     * Perform weight change and adjust state.
     *
     * If the clock was started and we come back to the clock owner, we set the state to TIME_STARTED If in a break, we
     * are careful not to update, unless the change causes an exit from the break (e.g. jury overrule on last lift)
     * Otherwise we update the displays.
     *
     * @param wc
     */
    private void doWeightChange(WeightChange wc) {
        Athlete changingAthlete = wc.getAthlete();
        Integer newWeight = changingAthlete.getNextAttemptRequestedWeight();
        logger.trace("&&1 cur={} curWeight={} changing={} newWeight={}", getCurAthlete(), curWeight, changingAthlete,
                newWeight);
        logger.trace("&&2 clockOwner={} clockLastStopped={} state={}", getClockOwner(),
                getAthleteTimer().getTimeRemainingAtLastStop(), state);

        boolean stopAthleteTimer = false;
        if (getClockOwner() != null && getAthleteTimer().isRunning()) {
            // time is running
            if (changingAthlete.equals(getClockOwner())) {
                logger.trace("&&3.A clock IS running for changing athlete {}", changingAthlete);
                // X is the current lifter
                // if a real change (and not simply a declaration that does not change weight),
                // make sure clock is stopped.
                if (curWeight != newWeight) {
                    logger.trace("&&3.A.A1 weight change for clock owner: clock running: stop clock");
                    getAthleteTimer().stop(); // memorize time
                    stopAthleteTimer = true; // make sure we broacast to clients
                    doWeightChange(wc, changingAthlete, getClockOwner(), stopAthleteTimer);
                } else {
                    logger.trace("&&3.A.B declaration at same weight for clock owner: leave clock running");
                    // no actual weight change. this is most likely a declaration.
                    // we do the call to trigger a notification on official's screens, but request
                    // that the clock keep running
                    doWeightChange(wc, changingAthlete, getClockOwner(), false);
                    return;
                }
            } else {
                logger.trace("&&3.B clock running, but NOT for changing athlete, do not update attempt board");
                weightChangeDoNotDisturb(wc);
                return;
            }
        } else if (getClockOwner() != null && !getAthleteTimer().isRunning()) {
            logger.trace("&&3.B clock NOT running for changing athlete {}", changingAthlete);
            // time was started (there is an owner) but is not currently running
            // time was likely stopped by timekeeper because coach signaled change of weight
            doWeightChange(wc, changingAthlete, getClockOwner(), true);
        } else {
            logger.trace("&&3.C1 no clock owner, time is not running");
            // time is not running
            recomputeLiftingOrder();
            // updateGlobalRankings(); // now done by recomputeLiftingOrder
            setStateUnlessInBreak(CURRENT_ATHLETE_DISPLAYED);
            logger.trace("&&3.C2 displaying, curAthlete={}, state={}", getCurAthlete(), state);
            uiDisplayCurrentAthleteAndTime(true, wc, false);
        }
    }

    private void doWeightChange(WeightChange wc, Athlete changingAthlete, Athlete clockOwner,
            boolean currentDisplayAffected) {
        recomputeLiftingOrder(currentDisplayAffected);
        // if the currentAthlete owns the clock, then the next ui update will show the
        // correct athlete and
        // the time needs to be restarted (state = TIME_STOPPED). Going to TIME_STOPPED
        // allows the decision to register if the announcer
        // forgets to start time.

        // otherwise we need to announce new athlete (state = CURRENT_ATHLETE_DISPLAYED)

        FOPState newState = state;
        if (clockOwner.equals(getCurAthlete()) && currentDisplayAffected) {
            newState = TIME_STOPPED;
        } else if (currentDisplayAffected) {
            newState = CURRENT_ATHLETE_DISPLAYED;
        }
        logger.trace("&&3.X change for {}, new cur = {}, displayAffected = {}, switching to {}", changingAthlete,
                getCurAthlete(), currentDisplayAffected, newState);
        setStateUnlessInBreak(newState);
        uiDisplayCurrentAthleteAndTime(currentDisplayAffected, wc, false);
        // updateGlobalRankings(); // now done by recomputeLiftingOrder
    }

    private Mixer getSoundMixer() {
        Platform platform2 = getPlatform();
        return platform2 == null ? null : platform2.getMixer();
    }

    private boolean isDecisionDisplayScheduled() {
        return decisionDisplayScheduled;
    }

    private synchronized boolean isDownEmitted() {
        return downEmitted;
    }

    private synchronized boolean isFinalWarningEmitted() {
        return finalWarningEmitted;
    }

    private synchronized boolean isInitialWarningEmitted() {
        return initialWarningEmitted;
    }

//    private void recomputeAndRefresh(FOPEvent e) {
//        recomputeLiftingOrder();
//        updateGlobalRankings();
//        pushOut(new UIEvent.SwitchGroup(this.getGroup(), this.getState(), this.getCurAthlete(),
//                e.getOrigin()));
//    }

    private void prepareDownSignal() {
        if (isEmitSoundsOnServer()) {
            try {
                downSignal = new Tone(getSoundMixer(), 1100, 1200, 1.0);
            } catch (IllegalArgumentException | LineUnavailableException e) {
                logger.error("{}\n{}", e.getCause(), LoggerUtils./**/stackTrace(e));
                broadcast("SoundSystemProblem");
            }
        }
    }

    /**
     * Compute events resulting from decisions received so far (down signal, stopping timer, all decisions entered,
     * etc.)
     */
    private void processRefereeDecisions(FOPEvent e) {
        int nbRed = 0;
        int nbWhite = 0;
        int nbDecisions = 0;
        for (int i = 0; i < 3; i++) {
            if (refereeDecision[i] != null) {
                if (refereeDecision[i]) {
                    nbWhite++;
                } else {
                    nbRed++;
                }
                nbDecisions++;
            }
        }
        goodLift = null;
        if (nbWhite == 2 || nbRed == 2) {
            if (!downEmitted) {
                emitDown(e);
                downEmitted = true;
            }
        }
        if (nbDecisions == 3) {
            goodLift = nbWhite >= 2;
            if (!isDecisionDisplayScheduled()) {
                showDecisionAfterDelay(this);
            }
        }
    }

    private void pushOutDone() {
        logger.debug("{}group {} done", getLoggingName(), group);
        UIEvent.GroupDone event = new UIEvent.GroupDone(this.getGroup(), null);
        // make sure the publicresults update carries the right state.
        setState(BREAK);
        setBreakType(BreakType.GROUP_DONE);
        pushOut(event);
    }

    private void recomputeCurrentLeaders(List<Athlete> rankedAthletes) {
        if (rankedAthletes == null || rankedAthletes.size() == 0) {
            setLeaders(null);
            return;
        }

        if (getCurAthlete() != null) {
            Category category = getCurAthlete().getCategory();
            List<Athlete> currentCategoryAthletes = (rankedAthletes).stream()
                    .filter(a -> a.getCategory().sameAs(category)).collect(Collectors.toList());

            boolean cjStarted2 = isCjStarted();
            // logger.trace("currentCategoryAthletes {} {}", currentCategoryAthletes, cjStarted2);
            if (!cjStarted2) {
                List<Athlete> snatchLeaders = AthleteSorter.resultsOrderCopy(currentCategoryAthletes, Ranking.SNATCH)
                        .stream().filter(a -> a.getBestSnatch() > 0 && a.isEligibleForIndividualRanking())
                        .limit(3)
                        .collect(Collectors.toList());
                setLeaders(snatchLeaders);
                // logger.trace("snatch leaders {} {}", snatchLeaders, currentCategoryAthletes);
            } else {
                List<Athlete> totalLeaders = AthleteSorter.resultsOrderCopy(currentCategoryAthletes, Ranking.TOTAL)
                        .stream().filter(a -> a.getTotal() > 0 && a.isEligibleForIndividualRanking())
                        .limit(3)
                        .collect(Collectors.toList());
                setLeaders(totalLeaders);
                // logger.trace("total leaders {} {}", totalLeaders, currentCategoryAthletes);
            }
        } else {
            setLeaders(null);
        }
    }

    private void recomputeLiftingOrder(boolean currentDisplayAffected) {
        // this is where lifting order is actually recomputed
        recomputeOrderAndRanks();
        if (getCurAthlete() == null) {
            pushOutDone();
            return;
        }

        int timeAllowed = getTimeAllowed();
        Integer attemptsDone = getCurAthlete().getAttemptsDone();
        logger.trace("{}recomputed lifting order curAthlete={} prevlifter={} time={} attemptsDone={} [{}]",
                getLoggingName(),
                getCurAthlete() != null ? getCurAthlete().getFullName() : "",
                getPreviousAthlete() != null ? getPreviousAthlete().getFullName() : "",
                timeAllowed,
                attemptsDone,
                LoggerUtils.whereFrom());
        if (currentDisplayAffected) {
            getAthleteTimer().setTimeRemaining(timeAllowed);
        }
        // for the purpose of showing team scores, this is good enough.
        // if the current athlete has done all lifts, the group is marked as done.
        // if editing the athlete later gives back an attempt, then the state change will take
        // place and subscribers will revert to current athlete display.
        boolean done = attemptsDone >= 6;
        if (done) {
            pushOutDone();
        }
        group.setDone(done);
    }

    /**
     * Reset decisions. Invoked when recomputing lifting order when a fresh clock is given.
     */
    private void resetDecisions() {
        refereeDecision = new Boolean[3];
        refereeTime = new Integer[3];
        refereeForcedDecision = false;
    }

    private void resetEmittedFlags() {
        setInitialWarningEmitted(false);
        setFinalWarningEmitted(false);
        setTimeoutEmitted(false);
        setDownEmitted(false);
        setDecisionDisplayScheduled(false);
    }

    private void setBreakParams(BreakStarted e, IBreakTimer breakTimer2, BreakType breakType2,
            CountdownType countdownType2) {
        this.setBreakType(breakType2);
        this.setCountdownType(countdownType2);
        getAthleteTimer().stop();

        if (e.isIndefinite() || countdownType2 == CountdownType.INDEFINITE) {
            breakTimer2.setIndefinite();
        } else if (countdownType2 == CountdownType.DURATION) {
            breakTimer2.setTimeRemaining(e.getTimeRemaining());
            breakTimer2.setEnd(null);
        } else {
            breakTimer2.setTimeRemaining(0);
            breakTimer2.setEnd(e.getTargetTime());
        }
        logger.trace("breakTimer2 {} isIndefinite={}", countdownType2, breakTimer2.isIndefinite());
    }

    private void setClockOwner(Athlete athlete) {
        logger.debug("{}setting clock owner to {} [{}]", getLoggingName(), athlete, LoggerUtils.whereFrom());
        this.clockOwner = athlete;
    }

    private void setClockOwnerInitialTimeAllowed(int timeAllowed) {
        this.clockOwnerInitialTimeAllowed = timeAllowed;
    }

    private void setCurAthlete(Athlete athlete) {
        // logger.trace("setting curAthlete to {} [{}]", athlete, LoggerUtils.whereFrom());
        this.curAthlete = athlete;
    }

    private void setDecisionDisplayScheduled(boolean decisionDisplayScheduled) {
        this.decisionDisplayScheduled = decisionDisplayScheduled;
    }

    /**
     * @param displayOrder the displayOrder to set
     */
    private void setDisplayOrder(List<Athlete> displayOrder) {
        this.displayOrder = displayOrder;
    }

    private synchronized void setDownEmitted(boolean downEmitted) {
        logger.trace("downEmitted {}", downEmitted);
        this.downEmitted = downEmitted;
    }

    private synchronized void setFinalWarningEmitted(boolean finalWarningEmitted) {
        logger.trace("finalWarningEmitted {}", finalWarningEmitted);
        this.finalWarningEmitted = finalWarningEmitted;
    }

    private synchronized void setInitialWarningEmitted(boolean initialWarningEmitted) {
        logger.trace("initialWarningEmitted {}", initialWarningEmitted);
        this.initialWarningEmitted = initialWarningEmitted;
    }

    private void setLiftingOrder(List<Athlete> liftingOrder) {
        this.liftingOrder = liftingOrder;
    }

    private void setPreviousAthlete(Athlete athlete) {
        logger.trace("setting previousAthlete to {}", getCurAthlete());
        this.previousAthlete = athlete;
    }

    /**
     * Don't interrupt break if official-induced break. Interrupt break if it is simply "group done".
     *
     * @param newState the state we want to go to if there is no break
     */
    private void setStateUnlessInBreak(FOPState newState) {
        if (state == INACTIVE) {
            // remain in INACTIVE state (do nothing)
        } else if (state == BREAK) {
            logger.debug("{}Break {}", getLoggingName(), state, getBreakType());
            // if in a break, we don't stop break timer on a weight change.
            if (getBreakType() == BreakType.GROUP_DONE) {
                // weight change in state GROUP_DONE can happen if there is a loading error
                // and there is no jury deliberation break -- the weight change is entered
                // directly
                // in this case, we need to go back to lifting.
                // set the state now, otherwise attempt board will ignore request to display if
                // in a break
                setState(newState);
                if (newState == CURRENT_ATHLETE_DISPLAYED) {
                    uiStartLifting(group, this);
                } else {
                    uiShowUpdatedRankings();
                }
                getBreakTimer().stop();
            } else {
                // remain in break state
                setState(BREAK);
            }
        } else {
            setState(newState);
        }
    }

    private synchronized void setTimeoutEmitted(boolean timeoutEmitted) {
        logger.trace("timeoutEmitted {}", timeoutEmitted);
        this.timeoutEmitted = timeoutEmitted;
    }

    private void setWeightAtLastStart() {
        setWeightAtLastStart(getCurAthlete().getNextAttemptRequestedWeight());
    }

    synchronized private void showDecisionAfterDelay(Object origin2) {
        logger.trace("{}scheduling decision display", getLoggingName());
        assert !isDecisionDisplayScheduled(); // caller checks.
        setDecisionDisplayScheduled(true); // so there are never two scheduled...
        new DelayTimer().schedule(() -> showDecisionNow(origin2), REVERSAL_DELAY);

    }

    /**
     * The decision is confirmed as official after the 3 second delay following majority. After this delay, manual
     * announcer intervention is required to change and announce.
     */
    private void showDecisionNow(Object origin) {
        logger.trace("requesting decision display");
        // we need to recompute majority, since they may have been reversal
        int nbWhite = 0;
        for (int i = 0; i < 3; i++) {
            nbWhite = nbWhite + (Boolean.TRUE.equals(refereeDecision[i]) ? 1 : 0);
        }

        if (nbWhite >= 2) {
            goodLift = true;
            this.setCjStarted((getCurAthlete().getAttemptsDone() > 3));
            getCurAthlete().successfulLift();
        } else {
            goodLift = false;
            this.setCjStarted((getCurAthlete().getAttemptsDone() > 3));
            getCurAthlete().failedLift();
        }
        getCurAthlete().resetForcedAsCurrent();
        AthleteRepository.save(getCurAthlete());
        uiShowRefereeDecisionOnSlaveDisplays(getCurAthlete(), goodLift, refereeDecision, refereeTime, origin);
        recomputeLiftingOrder();
        // updateGlobalRankings(); // now done in recomputeLiftingOrder
        setState(DECISION_VISIBLE);
        // tell ourself to reset after 3 secs.
        new DelayTimer().schedule(() -> fopEventBus.post(new DecisionReset(origin)), DECISION_VISIBLE_DURATION);
    }

    /**
     * Create a fake unanimous decision when overridden.
     *
     * @param e
     */
    private void simulateDecision(ExplicitDecision ed) {
        int now = (int) System.currentTimeMillis();
        if (getAthleteTimer().isRunning()) {
            getAthleteTimer().stop();
        }
        DecisionFullUpdate ne = new DecisionFullUpdate(ed.getOrigin(), ed.getAthlete(), ed.ref1, ed.ref2, ed.ref3, now,
                now, now);
        refereeForcedDecision = true;
        updateRefereeDecisions(ne);
        uiShowUpdateOnJuryScreen();
        // needed to make sure 2min rule is triggered
        this.setPreviousAthlete(getCurAthlete());
        this.setClockOwner(null);
        this.setClockOwnerInitialTimeAllowed(0);
    }

    /**
     * The decision is confirmed as official after the 3 second delay following majority. After this delay, manual
     * announcer intervention is required to change and announce.
     */
//    private void showExplicitDecision(ExplicitDecision e, Object origin) {
//        logger.trace("explicit decision display");
//        refereeDecision[0] = null;
//        refereeDecision[2] = null;
//        if (e.success) {
//            goodLift = true;
//            refereeDecision[1] = true;
//            getCurAthlete().successfulLift();
//        } else {
//            goodLift = false;
//            refereeDecision[1] = false;
//            getCurAthlete().failedLift();
//        }
//        getCurAthlete().resetForcedAsCurrent();
//        AthleteRepository.save(getCurAthlete());
//        uiShowRefereeDecisionOnSlaveDisplays(getCurAthlete(), goodLift, refereeDecision, refereeTime, origin);
//        recomputeLiftingOrder();
//        updateGlobalRankings();
//        setState(DECISION_VISIBLE);
//        // tell ourself to reset after 3 secs.
//        new DelayTimer().schedule(() -> fopEventBus.post(new DecisionReset(origin)), DECISION_VISIBLE_DURATION);
//    }

    private void transitionToBreak(BreakStarted e) {
        IBreakTimer breakTimer2 = getBreakTimer();
        BreakType breakType2 = e.getBreakType();
        CountdownType countdownType2 = e.getCountdownType();
        if (state == BREAK) {
            if ((breakType2 != getBreakType() || countdownType2 != getCountdownType())) {
                // changing the kind of break
                logger.trace("{}switching break type while in break : current {} new {}", getLoggingName(),
                        getBreakType(),
                        e.getBreakType());
                breakTimer2.stop();
                setBreakParams(e, breakTimer2, breakType2, countdownType2);
//                getFopEventBus().post(new BreakStarted(breakType2,countdownType2,e.getTimeRemaining(),e.getTargetTime(),e.getOrigin()));
                logger.trace("starting1");
                breakTimer2.start(); // so we restart in the new type
            } else {
                // we are in a break, resume.
                logger.trace("{}resuming break : current {} new {}", getLoggingName(), getBreakType(),
                        e.getBreakType());
                breakTimer2.setOrigin(e.getOrigin());
                logger.trace("starting2");
                breakTimer2.start();
            }
        } else {
            setState(BREAK);
            setBreakParams(e, breakTimer2, breakType2, countdownType2);
            logger.trace("stopping1 {} {} {}", breakType2, countdownType2, breakTimer2.isIndefinite());
            breakTimer2.stop(); // so we restart in the new type
        }
        // this will broadcast to all slave break timers
        if (!breakTimer2.isRunning()) {
            breakTimer2.setOrigin(e.getOrigin());
            logger.trace("starting3");
            breakTimer2.start();
        }
        logger.trace("started break timers {}", breakType2);
    }

    private void transitionToLifting(FOPEvent e, Group group2, boolean stopBreakTimer) {
        setWeightAtLastStart(0);
        logger.trace("transitionToLifting {} {} from:{}", e.getAthlete(), stopBreakTimer,
                LoggerUtils.whereFrom());

        Athlete clockOwner = getClockOwner();
        if (getCurAthlete() != null && getCurAthlete().equals(clockOwner)) {
            setState(TIME_STOPPED); // allows referees to enter decisions even if time is not restarted (which
                                    // sometimes happens).
        } else {
            if (getCurAthlete() != null) {
                // group already in progress
                loadGroup(group2, e.getOrigin(), false);
            } else {
                loadGroup(group2, e.getOrigin(), true);
            }

            setState(CURRENT_ATHLETE_DISPLAYED);
        }
        if (stopBreakTimer) {
            getBreakTimer().stop();
            setBreakType(null);
        }
        uiStartLifting(getGroup(), e.getOrigin());
        uiDisplayCurrentAthleteAndTime(true, e, false);
    }

    private void transitionToTimeRunning() {
        getAthleteTimer().start();
        if (!getCurAthlete().equals(getClockOwner())) {
            setClockOwner(getCurAthlete());
            setClockOwnerInitialTimeAllowed(getTimeAllowed());
        }
        resetEmittedFlags();
        prepareDownSignal();
        setWeightAtLastStart();

        // enable master to listening for decision
        setState(TIME_RUNNING);
    }

    @SuppressWarnings("unused")
    private void uiDisplayCurrentWeight() {
        Integer nextAttemptRequestedWeight = getCurAthlete().getNextAttemptRequestedWeight();
        uiEventLogger.info("requested weight: {} (from curAthlete {})", nextAttemptRequestedWeight,
                getCurAthlete().getShortName());
    }

    private synchronized void uiShowDownSignalOnSlaveDisplays(Object origin2) {
        boolean emitSoundsOnServer2 = isEmitSoundsOnServer();
        boolean downEmitted2 = isDownEmitted();
        uiEventLogger.debug("showDownSignalOnSlaveDisplays server={} emitted={}", emitSoundsOnServer2, downEmitted2);
        if (emitSoundsOnServer2 && !downEmitted2) {
            // sound is synchronous, we don't want to wait.
            new Thread(() -> {
                try {
                    downSignal.emit();
                } catch (IllegalArgumentException | LineUnavailableException e) {
                    broadcast("SoundSystemProblem");
                }
            }).start();
            setDownEmitted(true);
        }
        pushOut(new UIEvent.DownSignal(origin2));
    }

    private void uiShowPlates(BarbellOrPlatesChanged e) {
        pushOut(new UIEvent.BarbellOrPlatesChanged(e.getOrigin()));
    }

    private void uiShowRefereeDecisionOnSlaveDisplays(Athlete athlete2, Boolean goodLift2, Boolean[] refereeDecision2,
            Integer[] shownTimes, Object origin2) {
        uiEventLogger.debug("### showRefereeDecisionOnSlaveDisplays {}", athlete2);
        pushOut(new UIEvent.Decision(athlete2, goodLift2, refereeForcedDecision ? null : refereeDecision2[0],
                refereeDecision2[1],
                refereeForcedDecision ? null : refereeDecision2[2], origin2));
    }

    private void uiShowUpdatedRankings() {
        pushOut(new UIEvent.GlobalRankingUpdated(this));
    }

    private void uiShowUpdateOnJuryScreen() {
        uiEventLogger.debug("### uiShowUpdateOnJuryScreen");
        pushOut(new UIEvent.RefereeUpdate(getCurAthlete(), refereeForcedDecision ? null : refereeDecision[0],
                refereeDecision[1],
                refereeForcedDecision ? null : refereeDecision[2], refereeTime[0], refereeTime[1], refereeTime[2],
                this));
    }

    private void uiStartLifting(Group group2, Object origin) {
        pushOut(new UIEvent.StartLifting(group2, origin));
    }

    private void unexpectedEventInState(FOPEvent e, FOPState state) {
        // events not worth signaling
        if (e instanceof DecisionReset || e instanceof DecisionFullUpdate) {
            // ignore
            return;
        }

        logger./**/warn(getLoggingName() + " " + Translator.translate("Unexpected_Logging"),
                e.getClass().getSimpleName(), state);

        pushOut(new UIEvent.Notification(this.getCurAthlete(), e.getOrigin(), e, state));
    }

    private void updateRefereeDecisions(FOPEvent.DecisionFullUpdate e) {
        refereeDecision[0] = e.ref1;
        refereeTime[0] = e.ref1Time;
        refereeDecision[1] = e.ref2;
        refereeTime[1] = e.ref2Time;
        refereeDecision[2] = e.ref3;
        refereeTime[2] = e.ref3Time;
        processRefereeDecisions(e);
    }

    private void updateRefereeDecisions(FOPEvent.DecisionUpdate e) {
        refereeDecision[e.refIndex] = e.decision;
        refereeTime[e.refIndex] = 0;
        processRefereeDecisions(e);
    }

    /**
     * weight change while a lift is being performed (bar lifted above knees) Lifting order is recomputed, so the
     * app.owlcms.ui.displayselection can get it, but not the attempt board state.
     *
     * @param e
     * @param curAthlete
     */
    private void weightChangeDoNotDisturb(WeightChange e) {
        recomputeOrderAndRanks();
        uiDisplayCurrentAthleteAndTime(false, e, false);
        // updateGlobalRankings(); // now done in recomputeOrderAndRanks
    }

}
