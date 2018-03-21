package org.ucombinator.jaam.visualizer.controllers;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import org.ucombinator.jaam.tools.taint3.Address;
import org.ucombinator.jaam.visualizer.gui.SelectEvent;
import org.ucombinator.jaam.visualizer.graph.Graph;
import org.ucombinator.jaam.visualizer.layout.*;
import org.ucombinator.jaam.visualizer.main.Main;
import org.ucombinator.jaam.visualizer.state.StateVertex;
import org.ucombinator.jaam.visualizer.taint.*;
import soot.Value;
import soot.jimple.Constant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class TaintPanelController extends GraphPanelController<TaintVertex, TaintEdge>
        implements EventHandler<SelectEvent<TaintVertex>> {

    private HashMap<String, TaintAddress> fieldVertices;

    public TaintPanelController(Graph<TaintVertex, TaintEdge> graph) throws IOException {
        super(TaintRootVertex::new);
        fillFieldDictionary();
        graphContentGroup.addEventFilter(SelectEvent.TAINT_VERTEX_SELECTED, this);
        LayerFactory.getLayeredGraph(graph, (TaintRootVertex) this.immutableRoot);
    }

    public TaintRootVertex getVisibleRoot() {
        return (TaintRootVertex) this.visibleRoot;
    }

    public TaintRootVertex getImmutableRoot() {
        return (TaintRootVertex) this.immutableRoot;
    }

    public void drawGraph(HashSet<TaintVertex> verticesToDraw) {
        System.out.println("Drawing taint graph...");
        visibleRoot.setVisible(false);
        this.visibleRoot = ((TaintRootVertex) this.immutableRoot).constructVisibleGraph(verticesToDraw);
        LayoutAlgorithm.layout(visibleRoot);
        drawNodes(null, visibleRoot);
        drawEdges(visibleRoot);
        visibleRoot.setVisible(true);
    }

    public void addSelectHandler(BorderPane centerPane) {
        centerPane.addEventHandler(SelectEvent.STATE_VERTEX_SELECTED, onVertexSelect);
    }

    @Override
    public void handle(SelectEvent<TaintVertex> event) {
        TaintVertex vertex = event.getVertex();

        if (vertex.getType() == AbstractLayoutVertex.VertexType.ROOT) {
            System.out.println("Ignoring click on vertex root.");
            event.consume();
            return;
        }

        System.out.println("Received event from vertex " + vertex.toString());

        MainTabController currentFrame = Main.getSelectedMainTabController();
        //currentFrame.resetHighlighted(vertex);

        if(vertex instanceof TaintAddress) {
            currentFrame.setRightText((TaintAddress) vertex);
        }
        else if(vertex instanceof TaintSccVertex)
        {
            currentFrame.setRightText((TaintSccVertex) vertex);
        }
        else if(vertex instanceof TaintStmtVertex) {
            currentFrame.setRightText((TaintStmtVertex) vertex);
        }
        else {
            //currentFrame.bytecodeArea.setDescription();
            currentFrame.setTaintRightText("Text");
        }
    }

    // Draw the graph of taint addresses for the selected node, and addresses connected to them.
    private EventHandler<SelectEvent<StateVertex>> onVertexSelect = new EventHandler<SelectEvent<StateVertex>>() {
        @Override
        public void handle(SelectEvent<StateVertex> selectEvent) {

            StateVertex v = selectEvent.getVertex();
            Set<TaintVertex> methodAddresses = findAddressesByMethods(v.getMethodNames());
            System.out.println("Taint vertices in method: " + methodAddresses.size());
            drawConnectedVertices(methodAddresses);
        }
    };

    private void drawConnectedVertices(Set<TaintVertex> addresses) {
        long time1 = System.nanoTime();
        HashSet<TaintVertex> verticesToDraw = findConnectedAddresses(addresses);
        System.out.println("Taint vertices to draw: " + verticesToDraw.size());

        long time2 = System.nanoTime();
        // Redraw graph with only this set of vertices.
        TaintPanelController.this.drawGraph(verticesToDraw);
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
        else
        {
            System.out.println("\tWarning: Did not find taint vertex " + fieldId);
        }
    }

    private void fillFieldDictionary()
    {
        fieldVertices = new HashMap<>();

        ArrayList<TaintAddress> allFields = new ArrayList<>();

        this.immutableRoot.getFields(allFields);

        allFields.forEach(v -> {
            fieldVertices.put(v.getFieldId(), v);
        });
    }
}
