package org.ucombinator.jaam.visualizer.layout;

import org.ucombinator.jaam.visualizer.gui.VizPanel;

import java.util.HashSet;

/**
 * Created by timothyjohnson on 3/20/17.
 */
public class LayoutRootVertex extends AbstractLayoutVertex {

    private float[] hues;
    private int maxLoopHeight;

    public LayoutRootVertex() {
        super("root", VertexType.ROOT, false);
    }

    public String getRightPanelContent() {
        return "Root vertex";
    }

    public String getShortDescription() {
        return "Root vertex";
    }

    public boolean searchByMethod(String query, VizPanel mainPanel) {
        boolean found = false;
        for(AbstractLayoutVertex v : this.getInnerGraph().getVertices().values()) {
            found = found || v.searchByMethod(query, mainPanel);
        }

        this.setHighlighted(found, mainPanel);
        return found;
    }

    public HashSet<LayoutMethodVertex> getMethodVertices()
    {
        HashSet<LayoutMethodVertex> methodVertices = new HashSet<LayoutMethodVertex>();
        for(AbstractLayoutVertex v : this.getInnerGraph().getVertices().values()) {
            if(v instanceof LayoutMethodVertex)
                methodVertices.add((LayoutMethodVertex) v);
            else
                methodVertices.addAll(v.getMethodVertices());
        }

        return methodVertices;
    }

    public void computeHues()
	{
		this.maxLoopHeight = this.calcMaxLoopHeight();
		System.out.println("Max loop height: " + maxLoopHeight);
		this.setColor(maxLoopHeight + 1);
	}
}