/*
 * Experimental ROBOT plugin
 * Copyright © 2025 Damien Goutte-Gattat
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
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.incenp.obofoundry.robot;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;
import org.obolibrary.robot.QuotedEntityChecker;
import org.obolibrary.robot.providers.CURIEShortFormProvider;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxClassExpressionParser;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.SetOntologyID;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import com.google.common.base.Optional;

/**
 * A command to extract a subset from an ontology.
 * <p>
 * This command is intended to replace both OWLTools’
 * {@code --extract-ontology-subset} command (without the {@code --span-gaps}
 * feature) and OWLTools’ {@code --make-ontology-from-results} command.
 */
public class SubsetExtractCommand extends BasePlugin {

    public SubsetExtractCommand() {
        super("subset", "extract an ontology subset",
                "robot subset [--query DL-QUERY | --subset SUBSET | --term TERM | --term-file TERMFILE]");
        options.addOption("r", "reasoner", true, "reasoner to use");
        options.addOption(null, "imports", true, "if true (default), use axioms from imported modules");

        options.addOption("q", "query", true, "include the results of the given DL query in the subset");
        options.addOption("t", "term", true, "include the given term in the subset");
        options.addOption("T", "term-file", true, "include the terms listed in the given file in the subset");
        options.addOption("s", "subset", true, "include classes tagged with the specified subset property");

        options.addOption("a", "include-ancestors", true,
                "if true, include ancestor classes in the results of the DL query");
        options.addOption("S", "in-subset-property", true,
                "specify the property used to mark subsets (default is oboInOwl#inSubset)");

        options.addOption("f", "fill-gaps", true, "if true, fill gaps to closure");
        options.addOption(null, "no-dangling", true, "if true (default), exclude dangling classes when filling");
        options.addOption(null, "follow-property", true,
                "when filling gaps, only follow relations that use the given property");
        options.addOption(null, "follow-in", true, "when filling gaps, only include classes in the given prefix");
        options.addOption(null, "not-follow-in", true, "when filling gaps, exclude classes in the given prefix");

        options.addOption(null, "replace", true, "if true, replace the current ontology with the subset");
        options.addOption(null, "write-to", true, "write the subset to the specified file");
        options.addOption(null, "ontology-iri", true, "set the ontology IRI of the subset");
    }

    @Override
    public void performOperation(CommandState state, CommandLine line) throws Exception {
        OWLOntology ontology = state.getOntology();
        OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLReasoner reasoner = CommandLineHelper.getReasonerFactory(line).createReasoner(state.getOntology());
        boolean useImports = line.getOptionValue("imports", "true").equals("true");

        // Setting up the extractor
        SubsetExtractor extractor = new SubsetExtractor(state.getOntology(), reasoner);
        extractor.setFillGaps(line.getOptionValue("fill-gaps", "false").equals("true"));
        extractor.setExcludeDangling(line.getOptionValue("no-dangling", "true").equals("true"));
        extractor.includeImports(useImports);
        if ( line.hasOption("follow-property") ) {
            for ( String property : line.getOptionValues("follow-property") ) {
                extractor.followProperty(getIRI(property, "follow-property"));
            }
        }
        if ( line.hasOption("follow-in") ) {
            for ( String prefix : line.getOptionValues("follow-in") ) {
                extractor.includePrefix(prefix);
            }
        }
        if ( line.hasOption("not-follow-in") ) {
            for ( String prefix : line.getOptionValues("not-follow-in") ) {
                extractor.excludePrefix(prefix);
            }
        }

        // Setting up the initial subset
        Set<OWLClass> subset = new HashSet<>();

        // 1. From a DL query
        if ( line.hasOption("query") ) {
            QuotedEntityChecker checker = new QuotedEntityChecker();
            checker.addProperty(factory.getRDFSLabel());
            checker.addProvider(new CURIEShortFormProvider(ioHelper.getPrefixes()));
            checker.addAll(ontology);
            ManchesterOWLSyntaxClassExpressionParser p = new ManchesterOWLSyntaxClassExpressionParser(factory, checker);

            for ( String query : line.getOptionValues("query") ) {
                OWLClassExpression expr = p.parse(query);
                if ( expr.isNamed() ) {
                    subset.add(expr.asOWLClass());
                }
                subset.addAll(reasoner.getSubClasses(expr, false).getFlattened());
                subset.addAll(reasoner.getEquivalentClasses(expr).getEntitiesMinusTop());
                if ( line.getOptionValue("include-ancestors", "false").equals("true") ) {
                    subset.addAll(reasoner.getSuperClasses(expr, false).getFlattened());
                }
            }
        }

        // 2. From the name/IRI of a subset defined in the ontology
        if ( line.hasOption("subset") ) {
            for ( String subsetName : line.getOptionValues("subset") ) {
                IRI subsetIRI = ioHelper.createIRI(subsetName);
                if ( subsetIRI != null ) {
                    subset.addAll(extractor.getSubset(subsetIRI));
                } else {
                    subset.addAll(extractor.getSubset(subsetName));
                }
            }
        }

        // 3. From an explicit list of terms
        Set<IRI> terms = new HashSet<>();
        if ( line.hasOption("term") ) {
            for ( String term : line.getOptionValues("term") ) {
                terms.add(getIRI(term, "term"));
            }
        }
        if ( line.hasOption("term-file") ) {
            for ( String termFile : line.getOptionValues("term-file") ) {
                terms.addAll(readFileAsIRIs(termFile));
            }
        }
        for ( IRI term : terms ) {
            if ( ontology.containsClassInSignature(term, useImports ? Imports.INCLUDED : Imports.EXCLUDED) ) {
                subset.add(factory.getOWLClass(term));
            }
        }

        // Actual extraction
        OWLOntology subsetOntology = extractor.makeSubset(subset);
        if ( line.hasOption("ontology-iri") ) {
            Optional<IRI> ontologyIRI = Optional.of(getIRI(line.getOptionValue("ontology-iri"), "ontology-iri"));
            OWLOntologyID id = new OWLOntologyID(ontologyIRI, Optional.absent());
            SetOntologyID change = new SetOntologyID(subsetOntology, id);
            subsetOntology.getOWLOntologyManager().applyChange(change);
        }
        if ( line.hasOption("write-to") ) {
            ioHelper.saveOntology(subsetOntology, line.getOptionValue("write-to"));
        }
        if ( line.getOptionValue("replace", "false").equals("true") ) {
            state.setOntology(subsetOntology);
        }
    }
}
