/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/

package app.owlcms.ui.results;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.vaadin.crudui.crud.LazyCrudListener;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import app.owlcms.data.agegroup.AgeGroupRepository;
import app.owlcms.data.athlete.AthleteRepository;
import app.owlcms.data.athlete.Gender;
import app.owlcms.data.category.AgeDivision;
import app.owlcms.data.group.Group;
import app.owlcms.data.group.GroupRepository;
import app.owlcms.data.team.TeamTreeData;
import app.owlcms.data.team.TeamTreeItem;
import app.owlcms.fieldofplay.FieldOfPlay;
import app.owlcms.i18n.Translator;
import app.owlcms.init.OwlcmsFactory;
import app.owlcms.spreadsheet.JXLSCompetitionBook;
import app.owlcms.ui.crudui.OwlcmsCrudFormFactory;
import app.owlcms.ui.crudui.OwlcmsCrudGrid;
import app.owlcms.ui.crudui.OwlcmsGridLayout;
import app.owlcms.ui.shared.AthleteGridLayout;
import app.owlcms.ui.shared.IAthleteEditing;
import app.owlcms.ui.shared.OwlcmsContent;
import app.owlcms.ui.shared.OwlcmsRouterLayout;
import app.owlcms.ui.shared.RequireLogin;
import app.owlcms.utils.URLUtils;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Class ResultsContent.
 *
 * @author Jean-François Lamy
 */
@SuppressWarnings("serial")
@Route(value = "results/teamresults", layout = AthleteGridLayout.class)
public class TeamResultsContent extends VerticalLayout
        implements OwlcmsContent, RequireLogin, IAthleteEditing {

    final private static Logger logger = (Logger) LoggerFactory.getLogger(TeamResultsContent.class);
    final private static Logger jexlLogger = (Logger) LoggerFactory.getLogger("org.apache.commons.jexl2.JexlEngine");
    static {
        logger.setLevel(Level.INFO);
        jexlLogger.setLevel(Level.ERROR);
    }
    
    static final String TITLE = "TeamResults.Title";

    private Group currentGroup;

    private OwlcmsRouterLayout routerLayout;
    protected ComboBox<Group> topBarGroupSelect;

    protected FlexLayout topBar;
    
    private Button download;
    private Anchor finalPackage;
    private JXLSCompetitionBook xlsWriter;
    private String ageGroupPrefix;

    private AgeDivision ageDivision;


    //private ComboBox<Category> categoryFilter;
    private ComboBox<Gender> genderFilter;
    //private ComboBox<String> teamFilter;
    private ComboBox<String> topBarAgeGroupPrefixSelect;
    private ComboBox<AgeDivision> topBarAgeDivisionSelect;
    
    private OwlcmsCrudGrid<TeamTreeItem> crudGrid;
    private Location location;
    private UI locationUI;
    private DecimalFormat floatFormat;
    //private boolean teamFilterRecusion;
    private List<AgeDivision> adItems;

    /**
     * Instantiates a new announcer content. Does nothing. Content is created in
     * {@link #setParameter(BeforeEvent, String)} after URL parameters are parsed.
     */
    public TeamResultsContent() {
        super();
        OwlcmsFactory.waitDBInitialized();
    }

    @Override
    public void closeDialog() {
        crudGrid.getCrudLayout().hideForm();
        crudGrid.getGrid().asSingleSelect().clear();
    }

    /**
     * Get the content of the crudGrid. Invoked by refreshGrid. Not currently used because we are using instead a
     * TreeGrid and a LazyCrudListener<TeamTreeItem>()
     *
     * @see TreeDataProvider
     * @see org.vaadin.crudui.crud.CrudListener#findAll()
     */
    public Collection<TeamTreeItem> findAll() {
        List<TeamTreeItem> allTeams = new ArrayList<>();

        TeamTreeData teamTreeData = new TeamTreeData(getAgeGroupPrefix(), getAgeDivision(), getGenderFilter().getValue());
        Map<Gender, List<TeamTreeItem>> teamsByGender = teamTreeData.getTeamItemsByGender();

        List<TeamTreeItem> mensTeams = teamsByGender.get(Gender.M);
        if (mensTeams != null) {
            allTeams.addAll(mensTeams);
        }
        List<TeamTreeItem> womensTeams = teamsByGender.get(Gender.F);
        if (womensTeams != null) {
            allTeams.addAll(womensTeams);
        }

        return allTeams;
    }

    @Override
    public OwlcmsCrudGrid<?> getEditingGrid() {
        return crudGrid;
    }

    public ComboBox<Gender> getGenderFilter() {
        return genderFilter;
    }


    public Location getLocation() {
        return location;
    }

    public UI getLocationUI() {
        return locationUI;
    }

    /**
     * @see com.vaadin.flow.router.HasDynamicTitle#getPageTitle()
     */
    @Override
    public String getPageTitle() {
        return getTranslation(TITLE);
    }

    @Override
    public OwlcmsRouterLayout getRouterLayout() {
        return routerLayout;
    }

    public boolean isIgnoreGroupFromURL() {
        return false;
    }

    public void refresh() {
        crudGrid.refreshGrid();
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setLocationUI(UI locationUI) {
        this.locationUI = locationUI;
    }

    /**
     * Parse the http query parameters
     *
     * Note: because we have the @Route, the parameters are parsed *before* our parent layout is created.
     *
     * @param event     Vaadin navigation event
     * @param parameter null in this case -- we don't want a vaadin "/" parameter. This allows us to add query
     *                  parameters instead.
     *
     * @see app.owlcms.utils.queryparameters.FOPParameters#setParameter(com.vaadin.flow.router.BeforeEvent,
     *      java.lang.String)
     */
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        setLocation(event.getLocation());
        setLocationUI(event.getUI());
        QueryParameters queryParameters = getLocation().getQueryParameters();
        Map<String, List<String>> parametersMap = queryParameters.getParameters(); // immutable
        HashMap<String, List<String>> params = new HashMap<>(parametersMap);

        logger.debug("parsing query parameters");
        List<String> groupNames = params.get("group");
        if (!isIgnoreGroupFromURL() && groupNames != null && !groupNames.isEmpty()) {
            String groupName = groupNames.get(0);
            currentGroup = GroupRepository.findByName(groupName);
        } else {
            currentGroup = null;
        }
        if (currentGroup != null) {
            params.put("group", Arrays.asList(URLUtils.urlEncode(currentGroup.getName())));
        } else {
            params.remove("group");
        }
        params.remove("fop");

        // change the URL to reflect group
        event.getUI().getPage().getHistory().replaceState(null,
                new Location(getLocation().getPath(), new QueryParameters(params)));
    }

    @Override
    public void setRouterLayout(OwlcmsRouterLayout routerLayout) {
        this.routerLayout = routerLayout;
    }

    public void updateURLLocation(UI ui, Location location, Group newGroup) {
        // change the URL to reflect fop group
        HashMap<String, List<String>> params = new HashMap<>(
                location.getQueryParameters().getParameters());
        if (!isIgnoreGroupFromURL() && newGroup != null) {
            params.put("group", Arrays.asList(URLUtils.urlEncode(newGroup.getName())));
        } else {
            params.remove("group");
        }
        ui.getPage().getHistory().replaceState(null, new Location(location.getPath(), new QueryParameters(params)));
    }

    protected HorizontalLayout announcerButtons(FlexLayout topBar2) {
        return null;
    }

    /**
     * Gets the crudGrid.
     *
     * @return the crudGrid crudGrid
     *
     * @see app.owlcms.ui.shared.AthleteGridContent#createCrudGrid(app.owlcms.ui.crudui.OwlcmsCrudFormFactory)
     */
    protected OwlcmsCrudGrid<TeamTreeItem> createCrudGrid(OwlcmsCrudFormFactory<TeamTreeItem> crudFormFactory) {
        TreeGrid<TeamTreeItem> grid = new TreeGrid<>();
        grid.addHierarchyColumn(TeamTreeItem::formatName).setHeader(Translator.translate("Name"));
        grid.addColumn(TeamTreeItem::getGender).setHeader(Translator.translate("Gender"))
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(TeamTreeItem::getPoints, "points").setHeader(Translator.translate("TeamResults.Points"))
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(t -> formatDouble(t.getScore(), 3), "score")
                .setHeader(Translator.translate("Scoreboard.Sinclair"))
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(TeamTreeItem::formatProgress).setHeader(Translator.translate("TeamResults.Status"))
                .setTextAlign(ColumnTextAlign.END);

        OwlcmsGridLayout gridLayout = new OwlcmsGridLayout(TeamTreeItem.class);
        OwlcmsCrudGrid<TeamTreeItem> crudGrid = new OwlcmsCrudGrid<>(TeamTreeItem.class, gridLayout,
                crudFormFactory, grid) {
            @Override
            public void refreshGrid() {
                if (topBar == null) {
                    return;
                }
                //logger.debug("refreshing grid {} {} {}",getAgeGroupPrefix(), getAgeDivision(), genderFilter.getValue());
                TeamTreeData teamTreeData = new TeamTreeData(getAgeGroupPrefix(), getAgeDivision(), genderFilter.getValue());
                grid.setDataProvider(new TreeDataProvider<>(teamTreeData));
            }

            @Override
            protected void initToolbar() {
            }

            @Override
            protected void updateButtonClicked() {
                TeamTreeItem item = grid.asSingleSelect().getValue();
                if (item.getAthlete() == null) {
                    return;
                }

                // only edit non-lifting groups
                if (!checkFOP()) {
                    super.updateButtonClicked();
                }
            }

            @Override
            protected void updateButtons() {
            }
        };

        defineFilters(crudGrid);
        defineContent(crudGrid);
        crudGrid.setClickRowToUpdate(true);
        return crudGrid;
    }

    /**
     * Create the top bar.
     *
     * Note: the top bar is created before the content.
     *
     * @see #showRouterLayoutContent(HasElement) for how to content to layout and vice-versa
     *
     * @param topBar
     */
    private void createTopBar() {
        // logger.trace("createTopBar {}", LoggerUtils. stackTrace());
        // show arrow but close menu
        getAppLayout().setMenuVisible(true);
        getAppLayout().closeDrawer();

        H3 title = new H3();
        title.setText(getTranslation(TITLE));
        title.add();
        title.getStyle().set("margin", "0px 0px 0px 0px").set("font-weight", "normal");

        topBar = getAppLayout().getAppBarElementWrapper();
        xlsWriter = new JXLSCompetitionBook(true, UI.getCurrent());
        StreamResource href = new StreamResource(TITLE + "Report" + ".xls", xlsWriter);
        finalPackage = new Anchor(href, "");
        finalPackage.getStyle().set("margin-left", "1em");
        download = new Button(getTranslation(TITLE + ".Report"), new Icon(VaadinIcon.DOWNLOAD_ALT));

        topBarAgeGroupPrefixSelect = new ComboBox<>();
        topBarAgeGroupPrefixSelect.setPlaceholder(getTranslation("AgeGroup"));

        topBarAgeGroupPrefixSelect.setEnabled(false);
        topBarAgeGroupPrefixSelect.setClearButtonVisible(true);
        topBarAgeGroupPrefixSelect.setValue(null);
        topBarAgeGroupPrefixSelect.setWidth("8em");
        topBarAgeGroupPrefixSelect.setClearButtonVisible(true);
        topBarAgeGroupPrefixSelect.getStyle().set("margin-left", "1em");
        setAgeGroupPrefixSelectionListener();

        topBarAgeDivisionSelect = new ComboBox<>();
        topBarAgeDivisionSelect.setPlaceholder(getTranslation("AgeDivision"));
        adItems = AgeGroupRepository.allAgeDivisionsForAllAgeGroups();
        topBarAgeDivisionSelect.setItems(adItems);
        topBarAgeDivisionSelect.setItemLabelGenerator((ad) -> Translator.translate("Division." + ad.name()));
        topBarAgeDivisionSelect.setClearButtonVisible(true);
        topBarAgeDivisionSelect.setWidth("8em");
        topBarAgeDivisionSelect.getStyle().set("margin-left", "1em");
        setAgeDivisionSelectionListener();
    
        finalPackage.add(download);
        HorizontalLayout buttons = new HorizontalLayout(finalPackage);
        buttons.setAlignItems(FlexComponent.Alignment.BASELINE);

        topBar.getStyle().set("flex", "100 1");
        topBar.removeAll();
        topBar.add(title,
                topBarAgeDivisionSelect, topBarAgeGroupPrefixSelect
        /* , templateSelect */
        /* , buttons */
        );
        topBar.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        topBar.setFlexGrow(0.2, title);
        topBar.setAlignItems(FlexComponent.Alignment.CENTER);
    }

    public String getAgeGroupPrefix() {
        return ageGroupPrefix;
    }

    public void setAgeGroupPrefix(String ageGroupPrefix) {
        this.ageGroupPrefix = ageGroupPrefix;
    }

    public AgeDivision getAgeDivision() {
        return ageDivision;
    }

    public void setAgeDivision(AgeDivision ageDivision) {
        this.ageDivision = ageDivision;
    }

    /**
     * We do not control the groups on other screens/displays
     *
     * @param crudGrid the crudGrid that will be filtered.
     */
    protected void defineFilters(OwlcmsCrudGrid<TeamTreeItem> crudGrid2) {
//        if (teamFilter == null) {
//            teamFilter = new ComboBox<>();
//            teamFilter.setPlaceholder(getTranslation("Team"));
//            teamFilter.setClearButtonVisible(true);
//            teamFilter.addValueChangeListener(e -> {
//                if (!teamFilterRecusion) return;
//                crudGrid2.refreshGrid();
//            });
//            teamFilter.setWidth("10em");
//        }
//        crudGrid2.getCrudLayout().addFilterComponent(teamFilter);

        if (genderFilter == null) {
            genderFilter = new ComboBox<>();
            genderFilter.setPlaceholder(getTranslation("Gender"));
            genderFilter.setItems(Gender.M, Gender.F);
            genderFilter.setItemLabelGenerator((i) -> {
                return i == Gender.M ? getTranslation("Gender.M") : getTranslation("Gender.F");
            });
            genderFilter.setClearButtonVisible(true);
            genderFilter.addValueChangeListener(e -> {
                crudGrid2.refreshGrid();
            });
            genderFilter.setWidth("10em");
        } 
        crudGrid2.getCrudLayout().addFilterComponent(genderFilter);
        
//        if (categoryFilter == null) {
//            categoryFilter = new ComboBox<>();
//            categoryFilter.setClearButtonVisible(true);
//            categoryFilter.setPlaceholder(getTranslation("Category"));
//            categoryFilter.setClearButtonVisible(true);
//            categoryFilter.addValueChangeListener(e -> {
//                crudGrid2.refreshGrid();
//            });
//            categoryFilter.setWidth("10em");
//        }
//        crudGrid2.getCrudLayout().addFilterComponent(categoryFilter);
    }


    /**
     * We do not connect to the event bus, and we do not track a field of play (non-Javadoc)
     *
     * @see com.vaadin.flow.component.Component#onAttach(com.vaadin.flow.component.AttachEvent)
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        OwlcmsCrudFormFactory<TeamTreeItem> crudFormFactory = new TeamItemFormFactory(TeamTreeItem.class, this);
        crudGrid = createCrudGrid(crudFormFactory);
        fillHW(crudGrid, this);
        
        if (topBar == null) {
            createTopBar();
            //logger.debug("after createTopBar {}",getAgeDivision());
        }
        
        AgeDivision value = (adItems != null && adItems.size() > 0) ? adItems.get(0) : null;
        setAgeDivision(value);
        topBarAgeDivisionSelect.setValue(value);
    }

    /**
     * @return true if the current group is safe for editing -- i.e. not lifting currently
     */
    private boolean checkFOP() {
        Collection<FieldOfPlay> fops = OwlcmsFactory.getFOPs();
        FieldOfPlay liftingFop = null;
        search: for (FieldOfPlay fop : fops) {
            if (fop.getGroup() != null && fop.getGroup().equals(currentGroup)) {
                liftingFop = fop;
                break search;
            }
        }
        if (liftingFop != null) {
            Notification.show(
                    getTranslation("Warning_GroupLifting") + liftingFop.getName() + getTranslation("CannotEditResults"),
                    3000, Position.MIDDLE);
            logger.debug(getTranslation("CannotEditResults_logging"), currentGroup, liftingFop);
            subscribeIfLifting(currentGroup);
        } else {
            logger.debug(getTranslation("EditingResults_logging"), currentGroup, liftingFop);
        }
        return liftingFop != null;
    }

    private void defineContent(OwlcmsCrudGrid<TeamTreeItem> crudGrid) {
        crudGrid.setCrudListener(new LazyCrudListener<TeamTreeItem>() {
            @Override
            public TeamTreeItem add(TeamTreeItem user) {
                AthleteRepository.save(user.getAthlete());
                return user;
            }

            @Override
            public void delete(TeamTreeItem user) {
                AthleteRepository.delete(user.getAthlete());
            }

            @Override
            public DataProvider<TeamTreeItem, ?> getDataProvider() {
                return new TreeDataProvider<>(new TeamTreeData(getAgeGroupPrefix(), getAgeDivision(), getGenderFilter().getValue()));
            }

            @Override
            public TeamTreeItem update(TeamTreeItem user) {
                AthleteRepository.save(user.getAthlete());
                return user;
            }
        });
    }

    private String formatDouble(double d, int decimals) {
        if (floatFormat == null) {
            floatFormat = new DecimalFormat();
            floatFormat.setMinimumIntegerDigits(1);
            floatFormat.setMaximumFractionDigits(decimals);
            floatFormat.setMinimumFractionDigits(decimals);
            floatFormat.setGroupingUsed(false);
        }
        return floatFormat.format(d);
    }

    private void subscribeIfLifting(Group nGroup) {
        logger.debug("subscribeIfLifting {}", nGroup);
        Collection<FieldOfPlay> fops = OwlcmsFactory.getFOPs();
        currentGroup = nGroup;

        // go through all the FOPs
        for (FieldOfPlay fop : fops) {
            // unsubscribe from FOP -- ensures that we clean up if no group is lifting
            try {
                fop.getUiEventBus().unregister(this);
            } catch (Exception ex) {
            }
            try {
                fop.getFopEventBus().unregister(this);
            } catch (Exception ex) {
            }

            // subscribe to fop and start tracking if actually lifting
            if (fop.getGroup() != null && fop.getGroup().equals(nGroup)) {
                logger.debug("subscribing to {} {}", fop, nGroup);
                try {
                    fopEventBusRegister(this, fop);
                } catch (Exception ex) {
                }
                try {
                    uiEventBusRegister(this, fop);
                } catch (Exception ex) {
                }
            }
        }
    }
    

    private void setAgeDivisionSelectionListener() {
        topBarAgeDivisionSelect.addValueChangeListener(e -> {
            // the name of the resulting file is set as an attribute on the <a href tag that
            // surrounds the download button.
            AgeDivision ageDivisionValue = e.getValue();
            setAgeDivision(ageDivisionValue);
            //logger.debug("ageDivisionSelectionListener {}",ageDivisionValue);
            if (ageDivisionValue == null) {
                topBarAgeGroupPrefixSelect.setValue(null);
                topBarAgeGroupPrefixSelect.setItems(new ArrayList<String>());
                topBarAgeGroupPrefixSelect.setEnabled(false);
                topBarAgeGroupPrefixSelect.setValue(null);
                crudGrid.refreshGrid();
                return;
            }

            List<String> ageDivisionAgeGroupPrefixes;
            ageDivisionAgeGroupPrefixes = AgeGroupRepository.findActiveAndUsed(ageDivisionValue);

            topBarAgeGroupPrefixSelect.setItems(ageDivisionAgeGroupPrefixes);
            boolean notEmpty = ageDivisionAgeGroupPrefixes.size() > 0;
            topBarAgeGroupPrefixSelect.setEnabled(notEmpty);
            String first = (notEmpty && ageDivisionValue == AgeDivision.IWF) ? ageDivisionAgeGroupPrefixes.get(0)
                    : null;
            //logger.debug("ad {} ag {} first {} select {}", ageDivisionValue, ageDivisionAgeGroupPrefixes, first, topBarAgeGroupPrefixSelect);

            xlsWriter.setAgeDivision(ageDivisionValue);
            finalPackage.getElement().setAttribute("download",
                    "results" + (getAgeDivision() != null ? "_" + getAgeDivision().name()
                            : (ageGroupPrefix != null ? "_" + ageGroupPrefix : "_all")) + ".xls");
            
            String value = notEmpty ? first : null;
            //logger.debug("setting prefix to {}", value);
            topBarAgeGroupPrefixSelect.setValue(value);
            updateFilters(ageDivisionValue, first);
            
            if (crudGrid != null && value == null) {
                // if prefix is already null, does not refresh. Force it.
                crudGrid.refreshGrid();
            }

        });
    }

    private void setAgeGroupPrefixSelectionListener() {
        topBarAgeGroupPrefixSelect.addValueChangeListener(e -> {
            // the name of the resulting file is set as an attribute on the <a href tag that
            // surrounds the download button.
            String prefix = e.getValue();
            setAgeGroupPrefix(prefix);
            
            //logger.debug("ageGroupPrefixSelectionListener {}",prefix);
            // updateFilters(getAgeDivision(), getAgeGroupPrefix());
            xlsWriter.setAgeGroupPrefix(ageGroupPrefix);
            finalPackage.getElement().setAttribute("download",
                    "results" + (getAgeDivision() != null ? "_" + getAgeDivision().name()
                            : (ageGroupPrefix != null ? "_" + ageGroupPrefix : "_all")) + ".xls");
            

            if (crudGrid != null) {
                crudGrid.refreshGrid();
            }

        });
    }

    private void updateFilters(AgeDivision ageDivision2, String ageGroupPrefix2) {
//        List<Category> categories = CategoryRepository.findByGenderDivisionAgeBW(genderFilter.getValue(),
//                getAgeDivision(), null, null);
//        if (getAgeGroupPrefix() != null && !getAgeGroupPrefix().isBlank()) {
//            categories = categories.stream().filter((c) -> c.getAgeGroup().getCode().equals(getAgeGroupPrefix()))
//                    .collect(Collectors.toList());
//        }
//         logger.trace("updateFilters {}, {}, {}", ageDivision2, ageGroupPrefix2, categories);
//        categoryFilter.setItems(categories);
    }

}
