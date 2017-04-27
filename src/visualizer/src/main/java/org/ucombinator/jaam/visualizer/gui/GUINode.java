package org.ucombinator.jaam.visualizer.gui;

import java.util.ArrayList;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import org.ucombinator.jaam.visualizer.layout.AbstractLayoutVertex;
import org.ucombinator.jaam.visualizer.layout.LayoutEdge;
import org.ucombinator.jaam.visualizer.layout.AnimationHandler;
import org.ucombinator.jaam.visualizer.layout.LayoutRootVertex;
import org.ucombinator.jaam.visualizer.main.Main;

public class GUINode extends Pane
{
    protected static boolean showId = true;
    protected static final double TEXT_VERTICAL_PADDING = 15;
    protected static final double TEXT_HORIZONTAL_PADDING = 15;
	double dragX, dragY;
    public Rectangle rect;
    protected Text rectLabel;
    private AbstractLayoutVertex vertex;
	private GUINode parent;

	private ArrayList<LayoutEdge> edges = new ArrayList<LayoutEdge>();

    boolean isDragging;

    private double totalScaleX;
    private double totalScaleY;

    public GUINode(GUINode parent, AbstractLayoutVertex v)
    {
        super();
        this.parent = parent;
        this.vertex = v;
        this.vertex.setGraphics(this);
        
        this.rect = new Rectangle();
        //this.backRect = new Rectangle();
        this.rectLabel = new Text(v.getId() + ", " + v.getLoopHeight());
        this.rectLabel.setVisible(v.isLabelVisible());

        if(v instanceof LayoutRootVertex)
            this.getChildren().add(this.rect);
        else
            this.getChildren().addAll(this.rect, this.rectLabel);

        this.rectLabel.setTranslateX(TEXT_HORIZONTAL_PADDING);
        this.rectLabel.setTranslateY(TEXT_VERTICAL_PADDING);

        this.isDragging = false;
        this.totalScaleX = 1;
        this.totalScaleY = 1;

        this.addMouseEvents();
        this.setVisible(true);
    }
    
    public AbstractLayoutVertex getVertex() {
		return vertex;
	}

	public void setVertex(AbstractLayoutVertex vertex) {
		this.vertex = vertex;
	}

    public String toString()
    {
        return rectLabel.getText().toString();
    }

    public void setLabel(String text)
    {
        this.rectLabel.setText(text);
    }

    // Next several methods: Pass on calls to underlying rectangle
    public void setFill(Color c)
    {
    	this.rect.setFill(c);
    	if(vertex.getType() == AbstractLayoutVertex.VertexType.CHAIN){
        	Stop[] stops = new Stop[]{new Stop(0.6,c), new Stop(0.4,Color.WHITE)};
            this.rect.setFill(new LinearGradient(0, 0, 8, 8, false, CycleMethod.REPEAT, stops));
        } else if(vertex.getType() == AbstractLayoutVertex.VertexType.ROOT){
        	this.rect.setFill(javafx.scene.paint.Color.WHITE);
        }
    }

    public void setStroke(Color c)
    {
        this.rect.setStroke(c);
    }

    public void setStrokeWidth(double strokeWidth)
    {
        this.rect.setStrokeWidth(strokeWidth);
    }

    public void setArcHeight(double height)
    {
        this.rect.setArcHeight(height);
        //this.backRect.setArcHeight(height);
    }

    public void setArcWidth(double width)
    {
        this.rect.setArcWidth(width);
        //this.backRect.setArcWidth(width);
    }

    public void setTranslateLocation(double x, double y) {
        this.setTranslateX(x);
        this.setTranslateY(y);
        this.rectLabel.setTranslateX(TEXT_HORIZONTAL_PADDING);
        this.rectLabel.setTranslateY(TEXT_VERTICAL_PADDING);
    }

    public void setTranslateLocation(double x, double y, double width, double height)
    {
        this.setTranslateX(x);
        this.setTranslateY(y);
        this.rect.setWidth(width);
        this.rect.setHeight(height);
        this.rectLabel.setTranslateX(TEXT_HORIZONTAL_PADDING);
        this.rectLabel.setTranslateY(TEXT_VERTICAL_PADDING);
        //this.backRect.setWidth(width);
        //this.backRect.setHeight(height);
    }

    // Returns the bounding box for just the rectangle in the coordinate system for the parent of our node.
    public Bounds getRectBoundsInParent() {
        Bounds nodeBounds = this.getBoundsInParent();
        Bounds rectBounds = this.rect.getBoundsInParent();
        BoundingBox totalBounds = new BoundingBox(nodeBounds.getMinX() + rectBounds.getMinX(),
                nodeBounds.getMinY() + rectBounds.getMinY(), rectBounds.getWidth(), rectBounds.getHeight());
        return totalBounds;
    }

    public void printLocation() {
        Bounds bounds = this.getBoundsInParent();
        System.out.println("Node x = " + bounds.getMinX() + ", " + bounds.getMaxX());
        System.out.println("Node y = " + bounds.getMinY() + ", " + bounds.getMaxY());
    }

    // Halve the distance from the current opacity to 1.
    public void increaseOpacity()
    {
        this.rect.setOpacity((1 + this.rect.getOpacity()) / 2.0);	
    }

    // Halve the current opacity.
    public void decreaseOpacity()
    {
        this.rect.setOpacity((this.rect.getOpacity()) / 2.0);
    }

    public void addMouseEvents()
    {
        this.setOnMousePressed(onMousePressedEventHandler);
        this.setOnMouseDragged(onMouseDraggedEventHandler);
        this.setOnMouseReleased(onMouseReleasedEventHandler);
        this.setOnMouseEntered(onMouseEnteredEventHandler);
        this.setOnMouseExited(onMouseExitedEventHandler);
        this.setOnMouseClicked(new AnimationHandler());
    }

    // The next two functions compute the shift that must be applied to keep the
    // top left corner stationary when the node is scaled about its center.
    public double getXShift()
    {
        double currentWidth = this.getScaleX() * this.vertex.getWidth();
        double oldWidth = this.vertex.getWidth();
        return (oldWidth - currentWidth) / 2;
        //return 0;
    }

    public double getYShift()
    {
        double currentHeight = this.getScaleY() * this.vertex.getHeight();
        double oldHeight = this.vertex.getHeight();
        return (oldHeight - currentHeight) / 2;
        //return 0;
    }

    EventHandler<MouseEvent> onMousePressedEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {
            event.consume();
            GUINode node = (GUINode) event.getSource();

            dragX = node.getBoundsInParent().getMinX() - event.getScreenX();
            dragY = node.getBoundsInParent().getMinY() - event.getScreenY();
        }
    };

    EventHandler<MouseEvent> onMouseDraggedEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {
            event.consume();
            GUINode node = (GUINode) event.getSource();

            node.isDragging = true;
            double offsetX = event.getScreenX() + dragX;
            double offsetY = event.getScreenY() + dragY;
            double totalTranslateX = offsetX - node.getXShift();
            double totalTranslateY = offsetY - node.getYShift();
            node.setTranslateLocation(totalTranslateX, totalTranslateY);

            AbstractLayoutVertex v = GUINode.this.vertex;
            VizPanel mainPanel = Main.getOuterFrame().getCurrentFrame().getMainPanel();
            v.setX(mainPanel.invScaleX(offsetX));
            v.setY(mainPanel.invScaleY(offsetY));
            LayoutEdge.redrawEdges(v, false);
        }
    };

    EventHandler<MouseEvent> onMouseReleasedEventHandler = new EventHandler<MouseEvent>()
    {
        @Override
        public void handle(MouseEvent event)
        {
            event.consume();
            GUINode node = (GUINode) event.getSource();

            if (node.isDragging)
            {
                node.isDragging = false;
            }
        }
    };

    EventHandler onMouseEnteredEventHandler = new javafx.event.EventHandler()
    {
        @Override
        public void handle(Event event)
        {
            event.consume();
        	if (vertex.getSelfGraph() != null)
        	{
	        	for(LayoutEdge e : vertex.getSelfGraph().getEdges().values())
                {
	        		if(e.getSourceVertex() == vertex || e.getDestVertex() == vertex)
	        		{
	        		    Line line = e.getLine();
	        		    line.setStroke(Color.ORANGERED);
	        		    line.setStrokeWidth(line.getStrokeWidth() * 4.0);
	        		}
	        	}
        	}
        }
    };

	EventHandler onMouseExitedEventHandler = new javafx.event.EventHandler()
    {
        @Override
        public void handle(Event event)
        {
            event.consume();
            //getChildren().remove(rectLabel);
            
        	if(vertex.getSelfGraph() != null)
        	{
	        	for(LayoutEdge e : vertex.getSelfGraph().getEdges().values())
                {
	        		if (e.getSourceVertex() == vertex || e.getDestVertex() == vertex)
	        		{
	        		    Line line = e.getLine();
	        			line.setStroke(Color.BLACK);
                        line.setStrokeWidth(line.getStrokeWidth() / 4.0);
	        		}
	        	}
        	}
        }
    };

	public GUINode getParentNode() {
	    return this.parent;
    }

    public double getTotalParentScaleX() {
	    if (this.parent != null)
	        return this.parent.totalScaleX;
	    else return 1;
    }

    public double getTotalParentScaleY() {
	    if (this.parent != null)
	        return this.parent.totalScaleY;
	    else return 1;
    }

	public void setTotalScaleX(double scale) {
	    this.totalScaleX = scale;
    }

	public double getTotalScaleX() {
	    return this.totalScaleX;
    }

    public void setTotalScaleY(double scale) {
	    this.totalScaleY = scale;
    }

    public double getTotalScaleY() {
	    return this.totalScaleY;
    }

	public void setLabelVisible(boolean isLabelVisible) {
		vertex.setLabelVisible(isLabelVisible);
		this.rectLabel.setVisible(isLabelVisible);
	}
}