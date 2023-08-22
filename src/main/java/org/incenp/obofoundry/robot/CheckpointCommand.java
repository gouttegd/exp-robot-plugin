/*
 * Experimental ROBOT plugin
 * Copyright © 2023 Damien Goutte-Gattat
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the Gnu General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.incenp.obofoundry.robot;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;

public class CheckpointCommand extends BasePlugin {

    private OWLOntology checkedOntology = null;

    public CheckpointCommand() {
        super("checkpoint", "set and move to a checkpoint in a pipeline", "robot checkpoint");
        options.addOption("s", "set", false, "set the checkpoint");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        if ( line.hasOption("set") ) {
            OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
            checkedOntology = mgr.copyOntology(state.getOntology(), OntologyCopy.DEEP);
        } else {
            OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
            state.setOntology(mgr.copyOntology(checkedOntology, OntologyCopy.DEEP));
        }
    }

}
