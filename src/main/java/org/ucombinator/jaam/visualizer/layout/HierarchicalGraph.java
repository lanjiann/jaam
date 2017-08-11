package org.ucombinator.jaam.visualizer.layout;

import java.util.*;

public class HierarchicalGraph
{
    private HashSet<AbstractLayoutVertex> vertices;
    private HashMap<AbstractLayoutVertex, HashMap<AbstractLayoutVertex, LayoutEdge>> edges;

    public HierarchicalGraph()
    {
    	super();
    	this.vertices = new HashSet<>();
        this.edges = new HashMap<>();
    }

    public HashSet<AbstractLayoutVertex> getVertices() {
        return vertices;
    }

    public void setVertices(HashSet<AbstractLayoutVertex> vertices) {
        this.vertices = vertices;
    }

    public HashSet<LayoutEdge> getEdges() {
        HashSet<LayoutEdge> edgeSet = new HashSet<>();
        for (HashMap<AbstractLayoutVertex, LayoutEdge> outEdgeSet : edges.values()) {
            edgeSet.addAll(outEdgeSet.values());
        }
        return edgeSet;
    }

    public void setEdges(HashSet<LayoutEdge> edges) {
        this.edges = new HashMap<>();
        for (LayoutEdge edge : edges) {
            // TODO: use addEdge for this (but may not want addOutgoingNeighbor from addEdge)
            this.edges.putIfAbsent(edge.getSource(), new HashMap<>());
            this.edges.get(edge.getSource()).put(edge.getDest(), edge);
        }
    }

    public void addVertex(AbstractLayoutVertex vertex)
    {
        this.vertices.add(vertex);
        vertex.setSelfGraph(this);
    }

    public void deleteVertex(AbstractLayoutVertex vertex)
    {
        this.vertices.remove(vertex);
        vertex.setSelfGraph(null);
    }
    
    public void addEdge(LayoutEdge edge)
    {
        edge.getSource().addOutgoingNeighbor(edge.getDest());
        edge.getDest().addIncomingNeighbor(edge.getSource());
        this.edges.putIfAbsent(edge.getSource(), new HashMap<>());
        this.edges.get(edge.getSource()).put(edge.getDest(), edge);
    }
    
    public void deleteEdge(LayoutEdge edge)
    {
        edge.getSource().removeOutgoingAbstractNeighbor(edge.getDest());
        this.edges.get(edge.getSource()).remove(edge.getDest());
    }
    
    public String toString()
    {
        StringBuilder output = new StringBuilder();
        if(this.vertices.size() == 0)
            return "";
        
        Iterator<AbstractLayoutVertex> abstractVertexIter = this.vertices.iterator();
        output.append("Vertices: ");
        while(abstractVertexIter.hasNext())
        {
            AbstractLayoutVertex v = abstractVertexIter.next();
            output.append(v.getLabel() + ", ");
            output.append("\n");
            output.append("Inner graph: \n");
            output.append(v.getInnerGraph().toString());
            output.append("\n");
        }
        output.append("\n");
        
        Iterator<LayoutEdge> edgeIter = this.getEdges().iterator();
        output.append("Edges: ");
        while(edgeIter.hasNext()){
            LayoutEdge e = edgeIter.next();
            output.append("( " + e.getSource().getLabel() + "->" + e.getDest().getLabel() + " ), ");
        }
        output.append("\n");
        return output.toString();
    }
    
    public void printCoordinates(){
        Iterator<AbstractLayoutVertex> it = this.getVertices().iterator();
        while(it.hasNext())
        {
            AbstractLayoutVertex v = it.next();
            System.out.println(v.getStrID() + ", x=" + v.getX() + ", y=" + v.getY());
        }
    }
    
    public AbstractLayoutVertex getRoot() {
        if(this.vertices.size() == 0){
            //System.out.println("getRoot on empty graph");
            return null;
        }

        ArrayList<AbstractLayoutVertex> arrayList = new ArrayList<AbstractLayoutVertex>(this.vertices);
        Collections.sort(arrayList);
        //System.out.println("Root ID: " + arrayList.get(0).getId());

        // Return the first vertex with no incoming edges
        for(AbstractLayoutVertex v : arrayList) {
            if(v.getIncomingNeighbors().size() == 0)
                return v;
        }

        // Otherwise, return the first vertex, period.
        return arrayList.get(0);
    }

    public void deleteEdge(AbstractLayoutVertex src, AbstractLayoutVertex dst) {
        this.edges.get(src).remove(dst);
    }

    public boolean hasEdge(AbstractLayoutVertex src, AbstractLayoutVertex dst) {
        return this.edges.containsKey(src) && this.edges.get(src).containsKey(dst);
    }

//    public static ArrayList<LayoutEdge> computeDummyEdges(LayoutRootVertex root)
//    {
//        System.out.println("Creating dummy edges: start...");
//        ArrayList<LayoutEdge> dummies = new ArrayList<LayoutEdge>();
//
//        root.cleanAll();
//        for(AbstractLayoutVertex v : root.getInnerGraph().getVertices().values())
//            visit(v, new LinkedHashMap<String, AbstractLayoutVertex>(), dummies);
//        
//        System.out.println("Creating dummy edges: done!");
//        return dummies;
//    }

//    private static void visit(AbstractLayoutVertex root, HashMap<String, AbstractLayoutVertex> hash, ArrayList<LayoutEdge> dummies)
//    {
//        Iterator<AbstractLayoutVertex> it = root.getOutgoingNeighbors().iterator();
//        root.setVertexStatus(AbstractLayoutVertex.VertexStatus.BLACK);
//        String rootMethod;
//
//        if (root instanceof LayoutInstructionVertex)
//            rootMethod = ((LayoutInstructionVertex) root).getInstruction().getMethodName();
//        else // if (root instanceof LayoutMethodVertex)
//            rootMethod = ((LayoutMethodVertex) root).getMethodName();
//
//        while(it.hasNext())
//        {
//            AbstractLayoutVertex absVertex = it.next();
//            String nextVertexMethod;
//
//            if (absVertex instanceof LayoutInstructionVertex)
//                nextVertexMethod = ((LayoutInstructionVertex) absVertex).getInstruction().getMethodName();
//            else
//                nextVertexMethod = ((LayoutMethodVertex) absVertex).getMethodName();
//
//            if(absVertex.getVertexStatus() == AbstractLayoutVertex.VertexStatus.WHITE) {
//                if(!nextVertexMethod.equals(rootMethod))
//                {
//                    if(hash.containsKey(nextVertexMethod)) {
//                        System.out.println("Adding dummy edge: " + hash.get(nextVertexMethod).getId() + "-->" + absVertex.getId());
//                        dummies.add(new LayoutEdge(hash.get(nextVertexMethod), absVertex, LayoutEdge.EDGE_TYPE.EDGE_DUMMY));
//                    }
//                }
//
    //            hash.put(nextVertexMethod, absVertex);
    //            visit(absVertex, hash, dummies);
    //        }
    //    }
    //}
}
