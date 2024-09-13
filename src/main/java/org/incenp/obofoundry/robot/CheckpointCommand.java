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

/**
 * A plugin to add and return to “checkpoints” in a ROBOT pipeline.
 * <p>
 * Use the <code>checkpoint</code> command with the <code>-s</code> option to
 * create the checkpoint, that is, to save the current state of the ontology
 * being manipulated. Then, later in the pipeline, use the
 * <code>checkpoint</code> command without argument to restore the saved
 * ontology, discarding any changes that may have happened in the meantime.
 * <p>
 * The following example will merge two ontologies, set the checkpoint, run the
 * ELK reasoner over the merge product and save its output to disk, then revert
 * to the checkpoint before running another reasoner and saving the output to
 * disk. The checkpoint avoids having to run ROBOT and loading and merging the
 * ontologies twice (at the expense of requiring more memory, since a copy of
 * the entire ontology must be kept in the checkpoint).
 * 
 * <pre>
 * robot merge -i input1.owl -i input2.owl \
 *       checkpoint -s \
 *       reason -r ELK -o reasoned-with-elk.owl \
 *       checkpoint \
 *       reason -r HermiT -o reasoned-with-hermit.owl
 * </pre>
 */
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
