package org.ucombinator.jaam.visualizer.controllers;

import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.BorderPane;
import org.ucombinator.jaam.tools.taint3.Address;
import org.ucombinator.jaam.util.Loop;
import org.ucombinator.jaam.visualizer.graph.GraphTransform;
import org.ucombinator.jaam.visualizer.graph.GraphUtils;
import org.ucombinator.jaam.visualizer.gui.SelectEvent;
import org.ucombinator.jaam.visualizer.graph.Graph;
import org.ucombinator.jaam.visualizer.layout.*;
import org.ucombinator.jaam.visualizer.main.Main;
import org.ucombinator.jaam.visualizer.state.StateLoopVertex;
import org.ucombinator.jaam.visualizer.state.StateMethodVertex;
import org.ucombinator.jaam.visualizer.state.StateVertex;
import org.ucombinator.jaam.visualizer.taint.*;
import soot.SootMethod;
import soot.Value;
import soot.jimple.Constant;
import soot.jimple.internal.JimpleLocal;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TaintPanelController extends GraphPanelController<TaintVertex, TaintEdge>
        implements EventHandler<SelectEvent<TaintVertex>>, SetChangeListener<TaintVertex> {

    private GraphTransform<TaintRootVertex, TaintVertex> immAndVis;
    private HashMap<String, TaintAddress> fieldVertices;

    private boolean collapseAll = false, expandAll = false;

    // Graph is the statement graph
    public TaintPanelController(Graph<TaintVertex, TaintEdge> graph, CodeViewController codeController, MainTabController tabController) throws IOException {
        super(TaintRootVertex::new, tabController);

        // Custom event handlers
        graphContentGroup.addEventFilter(SelectEvent.TAINT_VERTEX_SELECTED, this);

        this.visibleRoot = new TaintRootVertex();
        this.immutableRoot = new TaintRootVertex();

        this.immutableRoot.setInnerGraph(this.cleanTaintGraph(graph, codeController));
        fillFieldDictionary();
        immAndVis = null;
    }

    public TaintRootVertex getVisibleRoot() {
        return (TaintRootVertex) this.visibleRoot;
    }

    public TaintRootVertex getImmutableRoot() {
        return (TaintRootVertex) this.immutableRoot;
    }

    public void drawGraph() {
        visibleRoot.setVisible(false);

        Set<TaintVertex> verticesToDraw = this.tabController.getImmutableTaintShown();
        // System.out.println("Vertices to draw: " + verticesToDraw.size());
        GraphTransform<TaintRootVertex, TaintVertex> immToFlatVisible = this.getImmutableRoot().constructVisibleGraph(verticesToDraw);
        GraphTransform<TaintRootVertex, TaintVertex> flatToLayerVisible = LayerFactory.getLayeredTaintGraph(immToFlatVisible.newRoot);
        immAndVis = GraphTransform.transfer(immToFlatVisible, flatToLayerVisible);
        this.visibleRoot = immAndVis.newRoot;

        if (expandAll) {
            for (TaintVertex v : visibleRoot.getInnerGraph().getVertices()) {
                if (v instanceof TaintMethodVertex) {
                    v.setExpanded(true);
                }
            }
            expandAll = false;
        }
        if (collapseAll) {
            for (TaintVertex v : visibleRoot.getInnerGraph().getVertices()) {
                if (v instanceof TaintMethodVertex) {
                    v.setExpanded(false);
                }
            }
            collapseAll = false;
        }

        LayoutAlgorithm.layout(visibleRoot);
        drawNodes(null, visibleRoot);
        drawEdges(visibleRoot);
        visibleRoot.setVisible(true);
    }

    public void redrawGraph() {
        // System.out.println("Redrawing loop graph...");
        this.graphContentGroup.getChildren().remove(this.visibleRoot.getGraphics());
        this.drawGraph();
    }

    public void addSelectHandler(BorderPane centerPane) {
        centerPane.addEventHandler(SelectEvent.STATE_VERTEX_SELECTED, onVertexSelect);
    }

    @Override
    public void redrawGraphAction(ActionEvent event) throws IOException {
        event.consume();
        this.redrawGraph();
    }

    @Override
    public void hideSelectedAction(ActionEvent event) throws IOException {
        event.consume();
        this.tabController.hideSelectedTaintNodes();
    }

    @Override
    public void hideUnrelatedAction(ActionEvent event) throws IOException {
        event.consume();
        this.tabController.hideUnrelatedToHighlightedTaint();
    }

    @Override
    public void expandAll(ActionEvent event) throws IOException {
        event.consume();
        expandAll = true;
        this.redrawGraph();
    }
    @Override
    public void collapseAll(ActionEvent event) throws IOException {
        event.consume();
        collapseAll = true;
        this.redrawGraph();
    }

    // Changes to the visible set
    @Override
    public void onChanged(Change<? extends TaintVertex> change) {
        // System.out.println("TaintPanel responding to change in visible set...");
        if (change.wasAdded()) {
            TaintVertex immV = change.getElementAdded();
            immV.setHidden();
            if (immAndVis != null && immAndVis.getNew(immV) != null) {
                immAndVis.getNew(immV).setHidden();
            }
        } else {
            TaintVertex immV = change.getElementRemoved();
            immV.setUnhidden();
            if (immAndVis != null && immAndVis.getNew(immV) != null) {
                immAndVis.getNew(immV).setUnhidden();
            }
        }
    }

    @Override
    public void handle(SelectEvent<TaintVertex> event) {
        TaintVertex vertex = event.getVertex(); // A visible vertex

        if (vertex.getType() == AbstractLayoutVertex.VertexType.ROOT) {
            System.out.println("Ignoring click on vertex root.");
            event.consume();
            return;
        }

        System.out.println("Received event from vertex " + vertex.toString());
        if(vertex instanceof TaintAddress) {
            this.tabController.setRightText((TaintAddress) vertex);
        }
        else if(vertex instanceof TaintSccVertex)
        {
            this.tabController.setRightText((TaintSccVertex) vertex);
        }
        else if(vertex instanceof TaintStmtVertex) {
            this.tabController.setRightText((TaintStmtVertex) vertex);
        }
        else if(vertex instanceof TaintMethodVertex) {
            this.tabController.setRightText((TaintMethodVertex) vertex);
        }
        else {
            this.tabController.setTaintRightText("Text");
        }
    }

    // Draw the graph of taint addresses for the selected state vertex, and addresses connected to them.
    private EventHandler<SelectEvent<StateVertex>> onVertexSelect = new EventHandler<SelectEvent<StateVertex>>() {
        @Override
        public void handle(SelectEvent<StateVertex> selectEvent) {
            StateVertex v = selectEvent.getVertex();
            Set<TaintVertex> startVertices = new HashSet<>();
            VizPanelController vizController = TaintPanelController.this.tabController.vizPanelController;
            if (v instanceof StateMethodVertex) {
                StateVertex immV = vizController.getImmutable(v);
                // If we click on a method vertex, we should get all taint addresses for that method.
                startVertices = findAddressesByMethods(immV.getMethodNames());
            }
            else if (v instanceof StateLoopVertex) {
                // Otherwise, if we click on a loop, we just want the addresses controlling the loop.
                StateVertex immV = vizController.getImmutable(v);
                Loop.LoopInfo loopInfo = ((StateLoopVertex) immV).getCompilationUnit().loopInfo();
                SootMethod method = ((StateLoopVertex) immV).getCompilationUnit().method();
                if (loopInfo instanceof Loop.UnidentifiedLoop) {
                    // Default to drawing methods
                    startVertices = findAddressesByMethods(immV.getMethodNames());
                }
                else if (loopInfo instanceof Loop.IteratorLoop) {
                    Value value = ((Loop.IteratorLoop) loopInfo).iterable();
                    addTaintVertex(startVertices, value, method);
                }
                else if (loopInfo instanceof Loop.ArrayLoop) {
                    Value value = ((Loop.ArrayLoop) loopInfo).iterable();
                    addTaintVertex(startVertices, value, method);
                }
                else if (loopInfo instanceof Loop.SimpleCountUpForLoop) {
                    Value valueLower = ((Loop.SimpleCountUpForLoop) loopInfo).lowerBound();
                    Value valueUpper = ((Loop.SimpleCountUpForLoop) loopInfo).upperBound();
                    Value valueIncrement = ((Loop.SimpleCountUpForLoop) loopInfo).increment();

                    addTaintVertex(startVertices, valueLower, method);
                    addTaintVertex(startVertices, valueUpper, method);
                    addTaintVertex(startVertices, valueIncrement, method);
                }
                else if (loopInfo instanceof Loop.SimpleCountDownForLoop) {
                    Value valueLower = ((Loop.SimpleCountDownForLoop) loopInfo).lowerBound();
                    Value valueUpper = ((Loop.SimpleCountDownForLoop) loopInfo).upperBound();
                    Value valueIncrement = ((Loop.SimpleCountDownForLoop) loopInfo).increment();

                    addTaintVertex(startVertices, valueLower, method);
                    addTaintVertex(startVertices, valueUpper, method);
                    addTaintVertex(startVertices, valueIncrement, method);
                }
            }
            System.out.println("Start vertices: " + startVertices.size());
            drawConnectedVertices(startVertices);
        }
    };

    private void addTaintVertex(Set<TaintVertex> taintVertices, Value value, SootMethod method) {
        TaintVertex v = getTaintVertex(value, method);
        if (v != null) {
            taintVertices.add(v);
        }
    }

    private TaintVertex getTaintVertex(Value value, SootMethod method) {

        for (TaintVertex v : this.getImmutableRoot().getInnerGraph().getVertices()) {

            if (!v.getInnerGraph().isEmpty()) {
                System.out.println("Found a non empty inner graph " + v);
            }

            if(v instanceof TaintAddress) {
                TaintAddress vAddr = (TaintAddress) v;
                if (testAddress(vAddr, value, method)) {
                    return v;
                }
            }
            else if (v instanceof TaintStmtVertex) { // Might be the loop
                for (TaintAddress a : ((TaintStmtVertex) v).getAddresses()) {
                    if (testAddress(a, value, method)) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private boolean testAddress(TaintAddress vAddr, Value value, SootMethod method) {
        Address addr = vAddr.getAddress();
        if (addr instanceof Address.Value) {
            Value taintValue = ((Address.Value) addr).sootValue();
            //System.out.println("Comparing values: " + taintValue.equivTo(value) + ", " + taintValue + ", " + value);
            //System.out.println("Method: " + addr.sootMethod().toString());
            //System.out.println("Classes: " + taintValue.getClass() + ", " + value.getClass());
            if (compareValues(taintValue, value, vAddr.getSootMethod(), method)) {
                //System.out.println("Found match!");
                return true;
            }
        }

        return false;
    }

    private TaintVertex getTaintVertexRec(TaintVertex v, Value value, SootMethod method) {
        if(v instanceof TaintAddress) {
            TaintAddress vAddr = (TaintAddress) v;
            Address addr = vAddr.getAddress();
            if (addr instanceof Address.Value) {
                Value taintValue = ((Address.Value) addr).sootValue();
                System.out.println("Comparing values: " + taintValue.equivTo(value) + ", " + taintValue + ", " + value);
                System.out.println("Method: " + addr.sootMethod().toString());
                System.out.println("Classes: " + taintValue.getClass() + ", " + value.getClass());
                if (compareValues(taintValue, value, vAddr.getSootMethod(), method)) {
                    System.out.println("Found match!");
                    return v;
                }
            }
        }

        for (TaintVertex w : v.getInnerGraph().getVertices()) {
            TaintVertex result = getTaintVertexRec(w, value, method);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private boolean compareValues(Value taintValue, Value value, SootMethod taintMethod, SootMethod method) {
        if (taintValue.equals(value)) {
            System.out.println("Equal taint values!");
            return true;
        }
        else if (taintValue.equivTo(value)) {
            System.out.println("Equivalent taint values!");
            return true;
        }
        else if (value instanceof JimpleLocal) {
            System.out.println("One JimpleLocal!");
            if (taintValue instanceof JimpleLocal) {
                System.out.println("Both JimpleLocal!");
                if (taintMethod.getSubSignature().equals(method.getSubSignature())) {
                    System.out.println("Equal methods!");
                    System.out.println("SubSignature: " + taintMethod.getSubSignature());
                    if (((JimpleLocal) taintValue).getName().equals(((JimpleLocal) value).getName())) {
                        System.out.println("Equal names!");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void drawConnectedVertices(Set<TaintVertex> addresses) {
        long time1 = System.nanoTime();
        HashSet<TaintVertex> verticesToDraw = findConnectedAddresses(addresses);
        this.tabController.getImmutableTaintShown().clear();
        this.tabController.getImmutableTaintShown().addAll(verticesToDraw);
        long time2 = System.nanoTime();
        this.drawGraph();
        long time3 = System.nanoTime();

        System.out.println("Time to compute connected vertices: " + (time2 - time1) / 1000000000.0);
        System.out.println("Time to draw graph: " + (time3 - time2) / 1000000000.0);
    }

    private HashSet<TaintVertex> findAddressesByMethods(Set<String> methodNames) {
        HashSet<TaintVertex> results = new HashSet<>();
        this.immutableRoot.searchByMethodNames(methodNames, results); // TODO: This step is a little inefficient.
        return results;
    }

    private HashSet<TaintVertex> findConnectedAddresses(Set<TaintVertex> startVertices) {
        HashSet<TaintVertex> ancestors = new HashSet<>();
        HashSet<TaintVertex> descendants = new HashSet<>();

        // TODO: This code is cleaner, but might take longer?
        for (TaintVertex v : startVertices) {
            ancestors.addAll(v.getAncestors());
            descendants.addAll(v.getDescendants());
        }

        HashSet<TaintVertex> allResults = new HashSet<>();
        allResults.addAll(startVertices);
        allResults.addAll(ancestors);
        allResults.addAll(descendants);

        for (TaintVertex v : allResults) {
            v.setColor(TaintVertex.defaultColor);
            if(v instanceof TaintAddress) {
                TaintAddress vAddr = (TaintAddress) v;
                Address addr = vAddr.getAddress();
                if (addr instanceof Address.Value) {
                    Value value = ((Address.Value) addr).sootValue();
                    if(value instanceof Constant) {
                        v.setColor(TaintVertex.constColor);
                    }
                }
            }
            else if (v instanceof TaintSccVertex) {
                v.setColor(TaintVertex.sccColor);
            }
            else if (startVertices.contains(v)) {
                v.setColor(TaintVertex.currMethodColor);
            } else if (ancestors.contains(v)) {
                if (descendants.contains(v)) {
                    v.setColor(TaintVertex.bothColor);
                }
                else {
                    v.setColor(TaintVertex.upColor);
                }
            }
            else if (descendants.contains(v)) {
                v.setColor(TaintVertex.downColor);
            }
        }

        return allResults;
    }

    public void showFieldTaintGraph(String fullClassName, String fieldName) {

        String fieldId = fullClassName + ":" + fieldName;
        TaintAddress a = fieldVertices.get(fieldId);

        if (a != null) {
            HashSet<TaintVertex> vertices = new HashSet<>();
            vertices.add(a);
            drawConnectedVertices(vertices);
        }
        else {
            System.out.println("\tWarning: Did not find taint vertex " + fieldId);
        }
    }

    private void fillFieldDictionary() {
        fieldVertices = new HashMap<>();

        ArrayList<TaintAddress> allFields = new ArrayList<>();

        this.immutableRoot.getFields(allFields);

        allFields.forEach(v -> {
            fieldVertices.put(v.getFieldId(), v);
        });
    }

    private Graph<TaintVertex, TaintEdge> cleanTaintGraph(Graph<TaintVertex, TaintEdge> graph, CodeViewController codeController) {
        return removeDegree2Addresses(removeNonCodeRootAddresses(graph, codeController));
    }

    private Graph<TaintVertex, TaintEdge> removeNonCodeRootAddresses(Graph<TaintVertex, TaintEdge> graph, CodeViewController codeController) {

        HashSet<TaintVertex> toRemove = graph.getSources().stream()
                .filter(v -> v instanceof TaintAddress && !codeController.haveCode(v.getClassName()))
                .collect(Collectors.toCollection(HashSet::new));

        for (TaintVertex v : toRemove) {
            graph.getEdges().removeAll(graph.getOutEdges(v));
            graph.getVertices().remove(v);
        }

        return graph;

    }

    private Graph<TaintVertex, TaintEdge> removeDegree2Addresses(Graph<TaintVertex, TaintEdge> graph) {

        TaintRootVertex temp = new TaintRootVertex();
        temp.setInnerGraph(graph);
        GraphTransform<TaintRootVertex, TaintVertex> transform = GraphUtils.constructVisibleGraph(temp, v -> {
            return graph.getOutEdges(v).size() != 1 || graph.getInEdges(v).size() != 1;
        }, TaintEdge::new);

        return transform.newRoot.getInnerGraph();
    }

    public HashSet<TaintVertex> getImmutable(HashSet<TaintVertex> visible) {
        return visible.stream()
                .flatMap(v -> v.expand().stream())
                .map(v -> immAndVis.getOld(v)).collect(Collectors.toCollection(HashSet::new));
    }

    public TaintVertex getImmutable(TaintVertex visible) {
        if (immAndVis.containsNew(visible)) {
            return immAndVis.getOld(visible);
        }
        return null;
    }

    // Selection are **visible** nodes
    // Returns the immutable nodes related to the selection of visible nodes
    public HashSet<TaintVertex> getUnrelatedVisible(HashSet<TaintVertex> selection) {

        HashSet<TaintVertex> keep = new HashSet<>();
        Graph<TaintVertex, TaintEdge> topLevel = getVisibleRoot().getInnerGraph();

        selection.forEach(v -> keep.addAll(v.getAncestors()));
        selection.forEach(v -> keep.addAll(v.getDescendants()));

        HashSet<TaintVertex> toHide = new HashSet<>();
        topLevel.getVertices().forEach(v -> {
            if (!keep.contains(v) && !keepAnyInterior(v, keep, toHide)) {
                toHide.add(v);
            }
        });

        return toHide;
    }

    private boolean keepAnyInterior(TaintVertex root, HashSet<TaintVertex> keep, HashSet<TaintVertex> toHide) {

        boolean foundAVertexToKeep = false;
        for (TaintVertex v : root.getInnerGraph().getVertices()) {
            if (keep.contains(v) || keepAnyInterior(v, keep, toHide)) {
                foundAVertexToKeep = true;
            }
            else {
                toHide.add(v);
            }
        }

        return foundAVertexToKeep;
    }
}
