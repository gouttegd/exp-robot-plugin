/*
 * Experimental ROBOT plugin
 * Copyright © 2024 Damien Goutte-Gattat
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

import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandState;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.util.OWLObjectTransformer;

/**
 * A command to expand short-form identifiers (“CURIEs”) into the corresponding
 * long-form IRIs within annotations.
 * <p>
 * That plugin will transform an annotation assertion axiom like this:
 * 
 * <pre>
 * AnnotationAssertion(oboInOwl:hasDbXref CL:0000000 "XAO:0003012")
 * </pre>
 * 
 * into this:
 * 
 * <pre>
 * AnnotationAssertion(oboInOwl:hasDbXref CL:0000000 <http://purl.obolibrary.org/obo/XAO_0003012>)
 * </pre>
 */
public class ExpandCurieCommand extends BasePlugin {

    public ExpandCurieCommand() {
        super("expand-curies", "expand CURIEs in annotations", "robot expand-curies -a <ANNOT>");
        options.addOption("a", "annotation", true,
                "expand CURIEs in annotations with the specified annotation property");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ont = state.getOntology();
        OWLDataFactory fac = ont.getOWLOntologyManager().getOWLDataFactory();

        HashSet<IRI> properties = new HashSet<IRI>();
        for ( String value : line.getOptionValues("annotation") ) {
            properties.add(getIRI(value, "annotation"));
        }
        if ( properties.isEmpty() ) {
            return;
        }

        OWLObjectTransformer<OWLAnnotation> t = new OWLObjectTransformer<>((x) -> true, (input) -> {
            if ( properties.contains(input.getProperty().getIRI()) ) {
                if ( input.getValue().isLiteral() ) {
                    IRI iri = ioHelper.createIRI(input.getValue().asLiteral().get().getLiteral());
                    if ( iri != null ) {
                        return fac.getOWLAnnotation(input.getProperty(), iri);
                    }
                }
            }
            return input;
        }, fac, OWLAnnotation.class);

        List<OWLOntologyChange> changes = t.change(ont);
        if ( !changes.isEmpty() ) {
            ont.getOWLOntologyManager().applyChanges(changes);
        }
    }
}
