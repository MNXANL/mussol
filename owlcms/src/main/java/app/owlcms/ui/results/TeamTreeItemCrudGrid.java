/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.ui.results;

import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.grid.Grid;

import app.owlcms.data.athlete.Athlete;
import app.owlcms.data.team.TeamTreeItem;
import app.owlcms.ui.crudui.OwlcmsCrudFormFactory;
import app.owlcms.ui.crudui.OwlcmsCrudGrid;
import app.owlcms.ui.crudui.OwlcmsGridLayout;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * This class attempts to locate the selected athlete from the in the lifting order.
 *
 * <p>
 * This is a workaround for two issues:
 * <li>Why does getValue() return a different object than that in the lifting order (initialization issue?)
 * <li>Why do we have to get the same object anyway (spurious comparison with == instead of getId() or .equals)
 * </p>
 *
 * @author Jean-François Lamy
 */
@SuppressWarnings("serial")
public class TeamTreeItemCrudGrid extends OwlcmsCrudGrid<TeamTreeItem> {

    final private Logger logger = (Logger) LoggerFactory.getLogger(TeamTreeItemCrudGrid.class);
    {
        logger.setLevel(Level.INFO);
    }

    Athlete match = null;

    public TeamTreeItemCrudGrid(Class<TeamTreeItem> domainType, OwlcmsGridLayout crudLayout,
            OwlcmsCrudFormFactory<TeamTreeItem> owlcmsCrudFormFactory, Grid<TeamTreeItem> grid) {
        super(domainType, crudLayout, owlcmsCrudFormFactory, grid);
    }

}
