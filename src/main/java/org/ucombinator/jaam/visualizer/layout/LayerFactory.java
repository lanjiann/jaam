package org.ucombinator.jaam.visualizer.layout;

import java.util.*;

import org.ucombinator.jaam.visualizer.graph.GraphUtils;
import org.ucombinator.jaam.visualizer.graph.Graph;
import org.ucombinator.jaam.visualizer.taint.*;

public class LayerFactory
{
    // TODO: Template these functions instead of copying them.
    public static void getLayeredGraph(Graph<StateVertex, StateEdge> graph, LayoutRootVertex root) {
        getStronglyConnectedComponentsGraph(graph, root);
    }

    private static void getStronglyConnectedComponentsGraph(Graph<StateVertex, StateEdge> graph, LayoutRootVertex root)
    {
        List<List<Integer>> sccs = GraphUtils.StronglyConnectedComponents(graph);
        System.out.println("Strongly connected components: " + sccs.size());

        Graph<StateVertex, StateEdge> sccGraph = root.getInnerGraph();

        // Need these two maps for the second pass to avoid having to look around for everything
        HashMap<StateVertex, StateVertex> inputToInner = new HashMap<>();
        HashMap<StateVertex, StateVertex> innerToSCC   = new HashMap<>();

        // First pass create SCC vertex and populate with layout vertices
        for (List<Integer> scc : sccs)
        {
            if(scc.size() > 1) {
                int sccId = sccGraph.getVertices().size();
                LayoutSccVertex sccVertex = new LayoutSccVertex(sccId, "SCC-" + sccId);
                sccGraph.addVertex(sccVertex);
                sccVertex.setSelfGraph(sccGraph);

                Graph<StateVertex, StateEdge> sccInner = sccVertex.getInnerGraph();

                for (Integer id : scc) {
                    StateVertex v = graph.getVertexById(id);
                    StateVertex innerVertex = upgradeStateVertex(v);
                    sccInner.addVertex(innerVertex);
                    innerVertex.setSelfGraph(sccInner);

                    // Add to hash tables for next pass
                    inputToInner.put(v, innerVertex);
                    innerToSCC.put(innerVertex, sccVertex);
                }
            }
            else {
                int id = scc.get(0);
                StateVertex v = graph.getVertexById(id);
                StateVertex newVertex = upgradeStateVertex(v);
                sccGraph.addVertex(newVertex);
                newVertex.setSelfGraph(sccGraph);

                // Add to hash tables for next pass
                inputToInner.put(v, newVertex);
                innerToSCC.put(newVertex, newVertex);
            }
        }

        // Second pass add edges between SCC vertices and add edges inside SCC vertices
        for (StateVertex inputV: graph.getVertices())
        {
            StateVertex v = inputToInner.get(inputV);
            StateVertex vSCC = innerToSCC.get(v);

            // TODO probably should have a better way
            if (vSCC.getInnerGraph().getVertices().size() > 0) // Am a SCC node
            {
                for (StateVertex inputN: graph.getOutNeighbors(inputV)) {
                    StateVertex n = inputToInner.get(inputN);
                    StateVertex nSCC = innerToSCC.get(n);

                    if(vSCC == nSCC) {
                        Graph<StateVertex, StateEdge> inner = vSCC.getInnerGraph();
                        inner.addEdge(new StateEdge(v, n));
                    } else {
                        sccGraph.addEdge(new StateEdge(vSCC, nSCC));
                    }
                }
            }
            else // Am some other type node not part of an SCC
            {
                for (StateVertex inputN: graph.getOutNeighbors(inputV)) {
                    StateVertex n = inputToInner.get(inputN);

                    StateVertex nSCC = innerToSCC.get(n);

                    sccGraph.addEdge(new StateEdge(vSCC, nSCC));
                }
            }
        }
    }

    public static void getLayeredGraph(Graph<TaintVertex, TaintEdge> graph, TaintRootVertex root) {
        getStronglyConnectedComponentsGraph(graph, root);
    }

    private static void getStronglyConnectedComponentsGraph(Graph<TaintVertex, TaintEdge> graph, TaintRootVertex root)
    {
        List<List<Integer>> sccs = GraphUtils.StronglyConnectedComponents(graph);
        System.out.println("Strongly connected components: " + sccs.size());

        Graph<TaintVertex, TaintEdge> sccGraph = root.getInnerGraph();

        // Need these two maps for the second pass to avoid having to look around for everything
        HashMap<TaintVertex, TaintVertex> inputToInner = new HashMap<>();
        HashMap<TaintVertex, TaintVertex> innerToSCC   = new HashMap<>();

        // First pass create SCC vertex and populate with layout vertices
        for (List<Integer> scc : sccs)
        {
            if(scc.size() > 1) {
                int sccId = sccGraph.getVertices().size();
                TaintSccVertex sccVertex = new TaintSccVertex(sccId, "SCC-" + sccId);
                sccGraph.addVertex(sccVertex);
                sccVertex.setSelfGraph(sccGraph);

                Graph<TaintVertex, TaintEdge> sccInner = sccVertex.getInnerGraph();

                for (Integer id : scc) {
                    TaintVertex v = graph.getVertexById(id);
                    TaintVertex innerVertex = upgradeTaintVertex(v);
                    sccInner.addVertex(innerVertex);
                    innerVertex.setSelfGraph(sccInner);

                    // Add to hash tables for next pass
                    inputToInner.put(v, innerVertex);
                    innerToSCC.put(innerVertex, sccVertex);
                }
            }
            else {
                TaintVertex v = graph.getVertexById(scc.get(0));
                TaintVertex newVertex = upgradeTaintVertex(v);
                sccGraph.addVertex(newVertex);
                newVertex.setSelfGraph(sccGraph);

                // Add to hash tables for next pass
                inputToInner.put(v, newVertex);
                innerToSCC.put(newVertex, newVertex);
            }
        }

        // Second pass add edges between SCC vertices and add edges inside SCC vertices
        for (TaintVertex inputV: graph.getVertices())
        {
            TaintVertex v = inputToInner.get(inputV);
            TaintVertex vSCC = innerToSCC.get(v);

            // TODO probably should have a better way
            if (vSCC.getInnerGraph().getVertices().size() > 0) // Am a SCC node
            {
                for (TaintVertex inputN: graph.getOutNeighbors(inputV)) {
                    TaintVertex n = inputToInner.get(inputN);
                    TaintVertex nSCC = innerToSCC.get(n);

                    if (vSCC == nSCC) {
                        Graph<TaintVertex, TaintEdge> inner = vSCC.getInnerGraph();
                        inner.addEdge(new TaintEdge(v, n));
                    } else {
                        sccGraph.addEdge(new TaintEdge(vSCC, nSCC));
                    }
                }
            }
            else // Am some other type node not part of an SCC
            {
                for(TaintVertex inputN: graph.getOutNeighbors(inputV)) {
                    TaintVertex n = inputToInner.get(inputN);

                    TaintVertex nSCC = innerToSCC.get(n);

                    sccGraph.addEdge(new TaintEdge(vSCC, nSCC));
                }
            }
        }
    }

    public static void getGraphByClass(Graph<StateVertex, StateEdge> graph, LayoutRootVertex root) {
        HashMap<String, ArrayList<StateVertex>> classGroups = GraphUtils.groupByClass(graph);
        Graph<StateVertex, StateEdge> classGraph = root.getInnerGraph();

        // Need this map for the second pass in which we add edges
        HashMap<StateVertex, LayoutClassVertex> innerToClass   = new HashMap<>();

        for (String className : classGroups.keySet()) {
            LayoutClassVertex classVertex = new LayoutClassVertex(className);
            classGraph.addVertex(classVertex);
            classVertex.setSelfGraph(classGraph);

            Graph<StateVertex, StateEdge> classInnerGraph = classVertex.getInnerGraph();

            for (StateVertex innerVertex : classGroups.get(className)) {
                classInnerGraph.addVertex(innerVertex);
                innerVertex.setSelfGraph(classInnerGraph);
                innerToClass.put(innerVertex, classVertex);
            }
        }

        for (String className : classGroups.keySet()) {
            for (StateVertex v : classGroups.get(className)) {
                for (StateVertex w : graph.getOutNeighbors(v)) {
                    LayoutClassVertex classVertexV = innerToClass.get(v);
                    LayoutClassVertex classVertexW = innerToClass.get(w);
                    if(classVertexV.equals(classVertexW)) {
                        classVertexV.getInnerGraph().addEdge(
                                new StateEdge(v, w));
                    } else {
                        classGraph.addEdge(
                                new StateEdge(classVertexV, classVertexW));
                    }
                }
            }
        }
    }

    // Takes an input vertex (a AbstractVertex which is actually a Loop or Method AbstractVertex) and returns a new
    // vertex of the correct type
    // TODO: Can we rewrite our algorithm to make this upgrading unnecessary?
    private static StateVertex upgradeStateVertex(StateVertex v)
    {
        StateVertex newVertex;

        if(v instanceof LayoutLoopVertex) {
            LayoutLoopVertex l = (LayoutLoopVertex) v;
            newVertex = new LayoutLoopVertex(l.getId(), l.getLabel(), l.getStatementIndex(),
                    l.getCompilationUnit());
            //newVertex = new LayoutLoopVertex(v.getId(), v.getLabel(), 0);
        }
        else if(v instanceof  LayoutMethodVertex) {
            LayoutMethodVertex l = (LayoutMethodVertex) v;
            newVertex = new LayoutMethodVertex(l.getId(), l.getLabel(), l.getCompilationUnit());
        }
        else {
            newVertex = null;
        }

        return newVertex;
    }

    // Takes an input vertex and upgrades it to a TaintAddress
    private static TaintVertex upgradeTaintVertex(TaintVertex v)
    {
        TaintVertex newVertex;

        if(v instanceof TaintStmtVertex) {
            TaintStmtVertex l = (TaintStmtVertex) v;
            newVertex = new TaintStmtVertex(l.getAddresses());
        } else if(v instanceof TaintAddress) {
            TaintAddress l = (TaintAddress) v;
            newVertex = new TaintAddress(l.getAddress());
        }
        else {
            newVertex = null;
        }

        return newVertex;
    }
}
