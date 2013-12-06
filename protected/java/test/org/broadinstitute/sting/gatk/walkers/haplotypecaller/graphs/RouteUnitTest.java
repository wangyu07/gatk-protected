/*
*  By downloading the PROGRAM you agree to the following terms of use:
*
*  BROAD INSTITUTE - SOFTWARE LICENSE AGREEMENT - FOR ACADEMIC NON-COMMERCIAL RESEARCH PURPOSES ONLY
*
*  This Agreement is made between the Broad Institute, Inc. with a principal address at 7 Cambridge Center, Cambridge, MA 02142 (BROAD) and the LICENSEE and is effective at the date the downloading is completed (EFFECTIVE DATE).
*
*  WHEREAS, LICENSEE desires to license the PROGRAM, as defined hereinafter, and BROAD wishes to have this PROGRAM utilized in the public interest, subject only to the royalty-free, nonexclusive, nontransferable license rights of the United States Government pursuant to 48 CFR 52.227-14; and
*  WHEREAS, LICENSEE desires to license the PROGRAM and BROAD desires to grant a license on the following terms and conditions.
*  NOW, THEREFORE, in consideration of the promises and covenants made herein, the parties hereto agree as follows:
*
*  1. DEFINITIONS
*  1.1 PROGRAM shall mean copyright in the object code and source code known as GATK2 and related documentation, if any, as they exist on the EFFECTIVE DATE and can be downloaded from http://www.broadinstitute/GATK on the EFFECTIVE DATE.
*
*  2. LICENSE
*  2.1   Grant. Subject to the terms of this Agreement, BROAD hereby grants to LICENSEE, solely for academic non-commercial research purposes, a non-exclusive, non-transferable license to: (a) download, execute and display the PROGRAM and (b) create bug fixes and modify the PROGRAM.
*  The LICENSEE may apply the PROGRAM in a pipeline to data owned by users other than the LICENSEE and provide these users the results of the PROGRAM provided LICENSEE does so for academic non-commercial purposes only.  For clarification purposes, academic sponsored research is not a commercial use under the terms of this Agreement.
*  2.2  No Sublicensing or Additional Rights. LICENSEE shall not sublicense or distribute the PROGRAM, in whole or in part, without prior written permission from BROAD.  LICENSEE shall ensure that all of its users agree to the terms of this Agreement.  LICENSEE further agrees that it shall not put the PROGRAM on a network, server, or other similar technology that may be accessed by anyone other than the LICENSEE and its employees and users who have agreed to the terms of this agreement.
*  2.3  License Limitations. Nothing in this Agreement shall be construed to confer any rights upon LICENSEE by implication, estoppel, or otherwise to any computer software, trademark, intellectual property, or patent rights of BROAD, or of any other entity, except as expressly granted herein. LICENSEE agrees that the PROGRAM, in whole or part, shall not be used for any commercial purpose, including without limitation, as the basis of a commercial software or hardware product or to provide services. LICENSEE further agrees that the PROGRAM shall not be copied or otherwise adapted in order to circumvent the need for obtaining a license for use of the PROGRAM.
*
*  3. OWNERSHIP OF INTELLECTUAL PROPERTY
*  LICENSEE acknowledges that title to the PROGRAM shall remain with BROAD. The PROGRAM is marked with the following BROAD copyright notice and notice of attribution to contributors. LICENSEE shall retain such notice on all copies.  LICENSEE agrees to include appropriate attribution if any results obtained from use of the PROGRAM are included in any publication.
*  Copyright 2012 Broad Institute, Inc.
*  Notice of attribution:  The GATK2 program was made available through the generosity of Medical and Population Genetics program at the Broad Institute, Inc.
*  LICENSEE shall not use any trademark or trade name of BROAD, or any variation, adaptation, or abbreviation, of such marks or trade names, or any names of officers, faculty, students, employees, or agents of BROAD except as states above for attribution purposes.
*
*  4. INDEMNIFICATION
*  LICENSEE shall indemnify, defend, and hold harmless BROAD, and their respective officers, faculty, students, employees, associated investigators and agents, and their respective successors, heirs and assigns, (Indemnitees), against any liability, damage, loss, or expense (including reasonable attorneys fees and expenses) incurred by or imposed upon any of the Indemnitees in connection with any claims, suits, actions, demands or judgments arising out of any theory of liability (including, without limitation, actions in the form of tort, warranty, or strict liability and regardless of whether such action has any factual basis) pursuant to any right or license granted under this Agreement.
*
*  5. NO REPRESENTATIONS OR WARRANTIES
*  THE PROGRAM IS DELIVERED AS IS.  BROAD MAKES NO REPRESENTATIONS OR WARRANTIES OF ANY KIND CONCERNING THE PROGRAM OR THE COPYRIGHT, EXPRESS OR IMPLIED, INCLUDING, WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER OR NOT DISCOVERABLE. BROAD EXTENDS NO WARRANTIES OF ANY KIND AS TO PROGRAM CONFORMITY WITH WHATEVER USER MANUALS OR OTHER LITERATURE MAY BE ISSUED FROM TIME TO TIME.
*  IN NO EVENT SHALL BROAD OR ITS RESPECTIVE DIRECTORS, OFFICERS, EMPLOYEES, AFFILIATED INVESTIGATORS AND AFFILIATES BE LIABLE FOR INCIDENTAL OR CONSEQUENTIAL DAMAGES OF ANY KIND, INCLUDING, WITHOUT LIMITATION, ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER BROAD SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
*
*  6. ASSIGNMENT
*  This Agreement is personal to LICENSEE and any rights or obligations assigned by LICENSEE without the prior written consent of BROAD shall be null and void.
*
*  7. MISCELLANEOUS
*  7.1 Export Control. LICENSEE gives assurance that it will comply with all United States export control laws and regulations controlling the export of the PROGRAM, including, without limitation, all Export Administration Regulations of the United States Department of Commerce. Among other things, these laws and regulations prohibit, or require a license for, the export of certain types of software to specified countries.
*  7.2 Termination. LICENSEE shall have the right to terminate this Agreement for any reason upon prior written notice to BROAD. If LICENSEE breaches any provision hereunder, and fails to cure such breach within thirty (30) days, BROAD may terminate this Agreement immediately. Upon termination, LICENSEE shall provide BROAD with written assurance that the original and all copies of the PROGRAM have been destroyed, except that, upon prior written authorization from BROAD, LICENSEE may retain a copy for archive purposes.
*  7.3 Survival. The following provisions shall survive the expiration or termination of this Agreement: Articles 1, 3, 4, 5 and Sections 2.2, 2.3, 7.3, and 7.4.
*  7.4 Notice. Any notices under this Agreement shall be in writing, shall specifically refer to this Agreement, and shall be sent by hand, recognized national overnight courier, confirmed facsimile transmission, confirmed electronic mail, or registered or certified mail, postage prepaid, return receipt requested.  All notices under this Agreement shall be deemed effective upon receipt.
*  7.5 Amendment and Waiver; Entire Agreement. This Agreement may be amended, supplemented, or otherwise modified only by means of a written instrument signed by all parties. Any waiver of any rights or failure to act in a specific instance shall relate only to such instance and shall not be construed as an agreement to waive any rights or fail to act in any other instance, whether or not similar. This Agreement constitutes the entire agreement among the parties with respect to its subject matter and supersedes prior agreements or understandings between the parties relating to its subject matter.
*  7.6 Binding Effect; Headings. This Agreement shall be binding upon and inure to the benefit of the parties and their respective permitted successors and assigns. All headings are for convenience only and shall not affect the meaning of any provision of this Agreement.
*  7.7 Governing Law. This Agreement shall be construed, governed, interpreted and applied in accordance with the internal laws of the Commonwealth of Massachusetts, U.S.A., without regard to conflict of laws principles.
*/
package org.broadinstitute.sting.gatk.walkers.haplotypecaller.graphs;

import org.broadinstitute.sting.BaseTest;
import org.jgrapht.EdgeFactory;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: valentin
 * Date: 9/5/13
 * Time: 11:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class RouteUnitTest extends BaseTest {


       @Test(dataProvider="isSuffixTestData")
       public void testIsSuffix(final Route<BaseVertex,BaseEdge> route, final Path<BaseVertex,BaseEdge> path, final boolean expectedResult) {
          Assert.assertEquals(route.isSuffix(path), expectedResult);
       }

       @DataProvider(name="isSuffixTestData")
       public Iterator<Object[]> isSuffixTestData() {
           return TEST_DATA.iterator();
       }

       private static final int[] TEST_EDGE_PAIRS1 = new int[] {
                      3 , 4,
                          4 , 5,
                              5, 7,
                                 7, 8,
                                    8, 9,
                          4 , 6,
                              6,       9,
                                       9,     11,
                                              11, 12,
       };



       private static final int[] TEST_EDGE_PAIRS = new int[] {
              1 , 2,
                  2 , 3,
                      3 , 4,
                          4 , 5,
                              5, 7,
                                 7, 8,
                                    8, 9,
                          4 , 6,
                              6,       9,
                                       9, 10,
                                          10, 11,
                                              11, 12,
                  2,          5,
                              5,                  12,

                      3,                             13,
                                                     13, 14,
                                                         14, 15
       };

    public static final EdgeFactory<BaseVertex, BaseEdge> TEST_GRAPH_EDGE_FACTORY = new EdgeFactory<BaseVertex, BaseEdge>() {
        @Override
        public BaseEdge createEdge(final BaseVertex baseVertex, final BaseVertex baseVertex2) {
            return new BaseEdge(false, 0);
        }
    };


    private static Map<Integer, BaseVertex> vertexByInteger = new HashMap<>();
    private static final BaseGraph<BaseVertex, BaseEdge> TEST_GRAPH = new BaseGraph<>(1, TEST_GRAPH_EDGE_FACTORY);
    private static final List<Object[]> TEST_DATA;


    static {
        for (int i = 0; i < TEST_EDGE_PAIRS.length; i += 2) {
            final int sourceInteger = TEST_EDGE_PAIRS[i];
            final int targetInteger = TEST_EDGE_PAIRS[i + 1];
            final BaseVertex sourceVertex = resolveVertexByInteger(sourceInteger);
            final BaseVertex targetVertex = resolveVertexByInteger(targetInteger);
            TEST_GRAPH.addEdge(sourceVertex, targetVertex);
        }
        Assert.assertEquals(1,TEST_GRAPH.getSources().size());
        final Deque<Path<BaseVertex,BaseEdge>> pendingPaths = new LinkedList<>();
        final Deque<Route<BaseVertex,BaseEdge>> pendingRoutes = new LinkedList<>();
        final List<Path<BaseVertex,BaseEdge>> allPossiblePaths = new LinkedList<>();
        final List<Route<BaseVertex,BaseEdge>> allPossibleRoutes = new LinkedList<>();
        for (final BaseVertex vertex : TEST_GRAPH.vertexSet()) {
            pendingPaths.add(new Path(vertex, TEST_GRAPH));
            pendingRoutes.add(new Route(vertex,TEST_GRAPH));
        }
        while (!pendingPaths.isEmpty()) { // !pendingRoutes.isEmpty();
            final Path<BaseVertex,BaseEdge> path = pendingPaths.remove();
            final Route<BaseVertex,BaseEdge> route = pendingRoutes.remove();
            final BaseVertex lastVertex = path.getLastVertex();
            allPossiblePaths.add(path);
            allPossibleRoutes.add(route);

            if (allPossiblePaths.size() % 100 == 0)
                Reporter.log("" + allPossiblePaths.size(), true);
            for (final BaseEdge edge : TEST_GRAPH.outgoingEdgesOf(lastVertex))
                pendingPaths.add(new Path<>(path,edge));
            for (final BaseEdge edge : TEST_GRAPH.outgoingEdgesOf(lastVertex))
                pendingRoutes.add(new Route<>(route,edge));
        }

        final int numberOfPaths = allPossiblePaths.size();
        final boolean[][] isSuffix = buildIsSuffixMatrix(allPossiblePaths, numberOfPaths);
        TEST_DATA = createTestData(allPossiblePaths,allPossibleRoutes,isSuffix);
    }

    private static boolean[][] buildIsSuffixMatrix(final List<Path<BaseVertex, BaseEdge>> allPossiblePaths, final int numberOfPaths) {
        final boolean[][] isSuffix = new boolean[numberOfPaths][numberOfPaths];
        final ListIterator<Path<BaseVertex,BaseEdge>> iIterator = allPossiblePaths.listIterator();
        for (int i = 0; i < numberOfPaths; i++) {
            isSuffix[i][i] = true;
            final ListIterator<Path<BaseVertex,BaseEdge>> jIterator = allPossiblePaths.listIterator(i + 1);
            final Path<BaseVertex,BaseEdge> iPath = iIterator.next();
            for (int j = i + 1; j < numberOfPaths; j++) {
                final Path<BaseVertex,BaseEdge> jPath = jIterator.next();
                if (iPath.getLastVertex() != jPath.getLastVertex()) {
                    isSuffix[i][j] = isSuffix[j][i] = false;
                } else {
                    isSuffix[i][j] = isSuffix[j][i] = true; // let assume they are suffix of each other by default.
                    final Path<BaseVertex,BaseEdge> shortPath;
                    final Path<BaseVertex,BaseEdge> longPath;
                    if (iPath.getEdges().size() <= jPath.getEdges().size()) {
                        shortPath = iPath;
                        longPath = jPath;
                    } else {
                        longPath = iPath;
                        shortPath = jPath;
                    }
                    final ListIterator<BaseEdge> longPathEdgesIterator = longPath.getEdges().listIterator(longPath.getEdges().size());
                    final ListIterator<BaseEdge> shortPathEdgesIterator = shortPath.getEdges().listIterator(shortPath.getEdges().size());

                    while (shortPathEdgesIterator.hasPrevious()) {
                        final BaseEdge shortEdge = shortPathEdgesIterator.previous();
                        final BaseEdge longEdge = longPathEdgesIterator.previous();
                        if (shortEdge != longEdge) {
                           isSuffix[i][j] = isSuffix[j][i] = false;
                            break;
                        }
                    }
                    if (isSuffix[i][j]) {
                        if (longPathEdgesIterator.hasPrevious()) {
                            if (longPath == iPath)
                                isSuffix[j][i] = false;
                            else
                                isSuffix[i][j] = false;
                        }
                    }
                }

            }
        }
        return isSuffix;
    }

    private static List<Object[]> createTestData(final List<Path<BaseVertex, BaseEdge>> allPossiblePaths, final List<Route<BaseVertex, BaseEdge>> allPossibleRoutes, final boolean[][] isSuffix) {
        final List<Object[]> result = new ArrayList<>(allPossiblePaths.size() * allPossiblePaths.size() * 2 );
        final Path<BaseVertex,BaseEdge>[] allPaths = allPossiblePaths.toArray(new Path[allPossiblePaths.size()]);
        final Route<BaseVertex,BaseEdge>[] allRoutes = allPossibleRoutes.toArray(new Route[allPossibleRoutes.size()]);
        final int numberOfPaths = allPaths.length;
        for (int i = 0; i < numberOfPaths; i++)
            for (int j = 0; j < numberOfPaths; j++) {
                result.add(new Object[] { allRoutes[i], allPaths[j], isSuffix[i][j] });
                result.add(new Object[] { allRoutes[i], allRoutes[j], isSuffix[i][j] });
                result.add(new Object[] { allRoutes[i], inverseRebuild(allRoutes[j]), isSuffix[i][j]});
            }

        return result;
    }

    private static Route<BaseVertex,BaseEdge> inverseRebuild(final Route<BaseVertex,BaseEdge> original) {
        final ListIterator<BaseEdge> it = original.getEdges().listIterator(original.length());
        Route<BaseVertex,BaseEdge> result = new Route<>(original.getLastVertex(),original.getGraph());
        while (it.hasPrevious()) {
            result = new Route<>(it.previous(),result);
        }
        return result;
    }

    private static BaseVertex resolveVertexByInteger(final int targetInteger) {
        if (vertexByInteger.containsKey(targetInteger))
            return vertexByInteger.get(targetInteger);
        else {
            int value = targetInteger;
            final StringBuffer stringBuffer = new StringBuffer();
            while (value > 0) {
               int c = value % 4;
               switch (c) {
                   case 0: stringBuffer.append('A'); break;
                   case 1: stringBuffer.append('C'); break;
                   case 2: stringBuffer.append('G'); break;
                   case 3: stringBuffer.append('T'); break;
               }
               value = value / 4;
            }
            if (stringBuffer.length() == 0) stringBuffer.append('A');
            final byte[] sequence = stringBuffer.reverse().toString().getBytes();
            final BaseVertex result = new BaseVertex(sequence);
            vertexByInteger.put(targetInteger, result);
            TEST_GRAPH.addVertex(result);
            return result;
        }

    }


}
