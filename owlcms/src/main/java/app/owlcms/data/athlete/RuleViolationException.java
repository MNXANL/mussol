/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.data.athlete;

import java.util.Locale;

import org.slf4j.LoggerFactory;

import app.owlcms.i18n.Translator;
import app.owlcms.init.OwlcmsSession;
import app.owlcms.utils.LoggerUtils;
import ch.qos.logback.classic.Logger;

/**
 * The Class RuleViolationException.
 */
@SuppressWarnings("serial")
public class RuleViolationException extends RuntimeException {

    public static class AttemptNumberTooLow extends RuleViolationException {
        /**
         * Athlete cannot move down to same weight as someone with a higher attempt number.
         *
         * Athlete A on first attempt requests more than an athlete on second attempt. Then A wants to move down to that
         * B weight. Cannot, because lower attempt number must lift first. This would give A unfair rest if A could lift
         * after B.
         *
         * @param requestedWeight
         * @param startNumber
         * @param referenceWeight
         * @param attemptNo
         */
        public AttemptNumberTooLow(Integer requestedWeight, Athlete athlete, int referenceWeight, int attemptNo) {
            super("RuleViolation.attemptNumberTooLow", requestedWeight,
                    athlete.getShortName(), referenceWeight, attemptNo);
        }
    }

    public static class DeclarationValueTooSmall extends RuleViolationException {
        /**
         * On attempt attemptNo, declaration be at least the automatic progression.
         *
         * @param attemptNo
         * @param newVal
         * @param iAutomaticProgression
         */
        public DeclarationValueTooSmall(int attemptNo, int newVal, int iAutomaticProgression) {
            super("RuleViolation.declarationValueTooSmall", (attemptNo % 3) + 1, newVal, iAutomaticProgression);
        }
    }

    public static class LastChangeTooLow extends RuleViolationException {
        /**
         * On attempt attemptNo, changes must be at least automatic progression.
         *
         * @param attemptNo
         * @param lastChange
         * @param iAutomaticProgression
         */
        public LastChangeTooLow(int attemptNo, int lastChange, int iAutomaticProgression) {
            super("RuleViolation.declaredChangesNotOk", (attemptNo % 3) + 1, lastChange, iAutomaticProgression);
        }
    }

    public static class LateDeclaration extends RuleViolationException {

        /**
         * Must declare before clock is started, or within first 30 seconds of running clock
         *
         * @param clock
         */
        public LateDeclaration(int clock) {
            super("RuleViolation.LateDeclaration", clock / 1000.0);
        }
    }

    public static class LiftedEarlier extends RuleViolationException {
        /**
         * Athlete cannot move down the weight on the bar was already taken by someone who lifted later on the previous
         * attempt.
         *
         * Athlete A lifted on first attempt, then B, due to the requested weight. A requests higher than B on second
         * attempt. A cannot move down to B's requested weight after B attempts, as this makes A lift out of order -- By
         * rule, A must come before B if they both attempt the same weight at the same attempt.
         *
         * @param requestedWeight
         * @param referenceAthlete
         * @param currentAthlete
         */
        public LiftedEarlier(Integer requestedWeight, Athlete referenceAthlete, Athlete currentAthlete) {
            super("RuleViolation.liftedEarlier", requestedWeight, referenceAthlete.getShortName(),
                    currentAthlete.getShortName());
        }
    }

    public static class LiftValueNotWhatWasRequested extends RuleViolationException {
        /**
         * When correcting an error manually (athlete on platform was not the one called), the value entered for the
         * lift must match the last declaration or change
         *
         * @param curLift
         * @param actualLift
         * @param lastDeclarationOrChange
         * @param liftedWeight
         */
        public LiftValueNotWhatWasRequested(int curLift, String actualLift, int lastDeclarationOrChange,
                int liftedWeight) {
            super("RuleViolation.liftValueNotWhatWasRequested", (curLift % 3) + 1, actualLift, lastDeclarationOrChange,
                    liftedWeight);
        }
    }

    public static class LotNumberTooHigh extends RuleViolationException {
        /**
         * This rule is there as precaution when weigh-in officials forgot to attribute start numbers. Athlete cannot
         * move down because same weight was attempted by an athlete with lower start number.
         *
         * On first lift, start number 1 cannot request higher than start 2 and, after 2 has been called, move down to
         * same weight (lifting out of order).
         *
         * @param requestedWeight
         * @param referenceLotNumber
         * @param curLotNumber
         */
        public LotNumberTooHigh(Integer requestedWeight, int referenceLotNumber, int curLotNumber) {
            super("RuleViolation.lotNumberTooHigh", requestedWeight,
                    referenceLotNumber, curLotNumber);
        }
    }

    public static class MustChangeBeforeFinalWarning extends RuleViolationException {

        /**
         * Must do change before final warning
         *
         * @param clock
         */
        public MustChangeBeforeFinalWarning(int clock) {
            super("RuleViolation.MustChangeBeforeFinalWarning", clock / 1000.0);
        }
    }

    public static class MustDeclareFirst extends RuleViolationException {

        /**
         * Must have declared legally before requesting change
         *
         * @param clock
         */
        public MustDeclareFirst(int clock) {
            super("RuleViolation.MustDeclareFirst", clock / 1000.0);
        }
    }

    public static class Rule15_20Violated extends RuleViolationException {
        /**
         * The 20kg rule (or equivalent Masters rule) is not respected.
         *
         * @param lastName
         * @param firstName
         * @param startNumber
         * @param snatch1Request
         * @param cleanJerk1Request
         * @param missing
         * @param qualTotal
         */
        public Rule15_20Violated(String lastName, String firstName, String startNumber, Integer snatch1Request,
                Integer cleanJerk1Request, int missing, int qualTotal) {
            super("RuleViolation.rule15_20Violated", lastName, firstName, startNumber, snatch1Request,
                    cleanJerk1Request, missing, qualTotal);
        }
    }

    public static class StartNumberTooHigh extends RuleViolationException {
        /**
         * Athlete cannot move down because same weight was attempted by an athlete with lower start number.
         *
         * On first lift, start number 1 cannot request higher than start 2 and, after 2 has been called, move down to
         * same weight (lifting out of order).
         *
         * @param requestedWeight
         * @param referenceStartNumber
         * @param curStartNumber
         */
        public StartNumberTooHigh(Integer requestedWeight, Athlete referenceAthlete, Athlete currentAthlete) {
            super("RuleViolation.startNumberTooHigh", requestedWeight,
                    referenceAthlete.getShortName(), currentAthlete.getShortName());
        }
    }

    public static class ValueBelowStartedClock extends RuleViolationException {
        /**
         * If the clock was started with weight W, any value smaller than W is wrong.
         *
         * Values equal to W are only ok if the lifting order rules are observed (see the various other exceptions)
         *
         * @param newVal
         * @param weightAtLastStart
         */
        public ValueBelowStartedClock(int newVal, Integer weightAtLastStart) {
            super("RuleViolation.valueBelowStartedClock", newVal, weightAtLastStart);
        }
    }

    public static class WeightBelowAlreadyLifted extends RuleViolationException {
        /**
         * Another athlete has already lifted more than what is requested.
         *
         * Bar cannot go down in weight. Unless error occurred, in which case T.O. can use "force as current lifter" to
         * disable validation.
         *
         * @param requestedWeight
         * @param startNumber
         * @param referenceWeight
         */
        public WeightBelowAlreadyLifted(Integer requestedWeight, Athlete athlete, int referenceWeight, int attemptNo) {
            super("RuleViolation.weightBelowAlreadyLifted", requestedWeight,
                    athlete.getShortName(), referenceWeight, attemptNo);
        }
    }

    private static final Logger logger = (Logger) LoggerFactory.getLogger(RuleViolationException.class);
    private static final long serialVersionUID = 8965943679108964933L;
    protected String messageKey;

    protected Object[] messageFormatData;

    /**
     * Instantiates a new rule violation exception.
     *
     * @param s    the s
     * @param objs the objs
     */
    private RuleViolationException(String s, Object... objs) {
        super(s);
        this.messageKey = s;
        this.messageFormatData = objs;
        logger./**/warn("{}{} [{}]", OwlcmsSession.getFopLoggingName(), getLocalizedMessage(Locale.ENGLISH), LoggerUtils.whereFrom(1));
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Throwable#getLocalizedMessage()
     */
    @Override
    public String getLocalizedMessage() {
        final Locale locale1 = OwlcmsSession.getLocale();
        return Translator.translate(this.messageKey, locale1, messageFormatData);
    }

    /**
     * Gets the localized message.
     *
     * @param locale1 the locale 1
     * @return the localized message
     */
    public String getLocalizedMessage(Locale locale1) {
        return Translator.translate(this.messageKey, locale1, messageFormatData);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return getLocalizedMessage();
    }

    public String getMessageKey() {
        return messageKey;
    }
}
