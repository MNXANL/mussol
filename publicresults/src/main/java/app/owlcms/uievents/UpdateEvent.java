/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.uievents;

public class UpdateEvent {

    private String leaders;
    private String categoryName;
    private boolean wideTeamNames;
    private String athletes;
    private String liftsDone;
    private String translationMap;
    private String attempt;
    private String fullName;
    private String groupName;
    private boolean hidden;
    private Integer startNumber;
    private String teamName;
    private Integer weight;
    private Integer timeAllowed;
    private String fopName;
    private String fopState;
    private String competitionName;
    private Boolean isBreak;
    private BreakType breakType;
    private Integer breakRemaining;
    private boolean indefinite;

    public UpdateEvent() {
        setLeaders(leaders);
    }

    public String getAthletes() {
        return this.athletes;
    }

    public String getAttempt() {
        return attempt;
    }

    public Integer getBreakRemaining() {
        return breakRemaining;
    }

    public BreakType getBreakType() {
        return breakType;
    }

    public String getCategoryName() {
        return this.categoryName;
    }

    public String getCompetitionName() {
        return this.competitionName;
    }

    public String getFopName() {
        return fopName;
    }

    public String getFopState() {
        return fopState;
    }

    public String getFullName() {
        return fullName;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean getHidden() {
        return hidden;
    }

    public String getLeaders() {
        return leaders;
    }

    public String getLiftsDone() {
        return this.liftsDone;
    }

    public Integer getStartNumber() {
        return startNumber;
    }

    public String getTeamName() {
        return teamName;
    }

    public Integer getTimeAllowed() {
        return this.timeAllowed;
    }

    public String getTranslationMap() {
        return this.translationMap;
    }

    public Integer getWeight() {
        return weight;
    }

    public boolean getWideTeamNames() {
        return this.wideTeamNames;
    }

    public Boolean isBreak() {
        return isBreak;
    }

    public void setAthletes(String athletes) {
        this.athletes = athletes;
    }

    public void setAttempt(String parameter) {
        this.attempt = parameter;
    }

    public void setBreak(Boolean isBreak) {
        this.isBreak = isBreak;
    }

    public void setBreakRemaining(Integer milliseconds) {
        this.breakRemaining = milliseconds;
    }

    public void setBreakType(BreakType bt) {
        this.breakType = bt;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public void setCompetitionName(String competitionName) {
        this.competitionName = competitionName;
    }

    public void setFopName(String parameter) {
        this.fopName = parameter;
    }

    public void setFopState(String parameter) {
        this.fopState = parameter;
    }

    public void setFullName(String parameter) {
        this.fullName = parameter;
    }

    public void setGroupName(String parameter) {
        this.groupName = parameter;
    }

    public void setHidden(boolean parameter) {
        this.hidden = parameter;
    }

    public void setLeaders(String leaders) {
        this.leaders = leaders;
    }

    public void setLiftsDone(String liftsDone) {
        this.liftsDone = liftsDone;
    }

    public void setStartNumber(Integer parameter) {
        this.startNumber = parameter;
    }

    public void setTeamName(String parameter) {
        this.teamName = parameter;
    }

    public void setTimeAllowed(Integer integer) {
        this.timeAllowed = integer;
    }

    public void setTranslationMap(String translationMap) {
        this.translationMap = translationMap;
    }

    public void setWeight(Integer integer) {
        this.weight = integer;
    }

    public void setWideTeamNames(boolean wideTeamNames) {
        this.wideTeamNames = wideTeamNames;
    }

    @Override
    public String toString() {
        return "UpdateEvent [groupName=" + groupName + ", timeAllowed=" + timeAllowed + ", fopName=" + fopName
                + ", fopState=" + fopState + ", isBreak=" + isBreak + ", breakType=" + breakType + ", breakRemaining="
                + breakRemaining + "]";
    }

    public boolean isIndefinite() {
        return this.indefinite;
    }

    public void setIndefinite(boolean indefinite) {
        this.indefinite = indefinite;
    }
    
    

}
