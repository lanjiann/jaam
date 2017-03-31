package org.ucombinator.jaam.visualizer.layout;

import java.util.Iterator;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.ucombinator.jaam.visualizer.graph.AbstractVertex;
import org.ucombinator.jaam.visualizer.graph.Edge;
import org.ucombinator.jaam.visualizer.gui.GUINode;
import org.ucombinator.jaam.visualizer.main.Parameters;

public class AnimationHandler implements javafx.event.EventHandler<javafx.scene.input.MouseEvent>
{
	public static int transitionTime = 300; // Milliseconds per transition

	@Override
	public void handle(MouseEvent event) {
		EventType<MouseEvent> type = (EventType<MouseEvent>) event.getEventType();
		if(type.equals(MouseEvent.MOUSE_CLICKED))
		{
			if(event.getButton().equals(MouseButton.PRIMARY))
			{
				switch (event.getClickCount())
				{
					case 1:
						handlePrimarySingleClick(event);
						break;
					case 2:
						handlePrimaryDoubleClick(event);
						break;
					default:
						break;
				}
			}
			else if(event.getButton().equals(MouseButton.SECONDARY)) {}
			else if(event.getButton().equals(MouseButton.MIDDLE)) {}
		}
		else
		{
			System.out.println("This line should never be printed since we add the handler by setOnMouseClicked");
		}
	}
	
	private void collapsing(AbstractLayoutVertex v)
	{
		System.out.println("Collapsing node: " + v.getId() + ", " + v.getGraphics().toString());
		Iterator<Node> it = v.getGraphics().getChildren().iterator();
		while(it.hasNext())
		{
			final Node n = it.next();
			if(!n.getClass().equals(Rectangle.class))
			{
				FadeTransition ft = new FadeTransition(Duration.millis(transitionTime), n);
				ft.setToValue(0.0);
				
				ft.setOnFinished(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {		
						n.setVisible(false);
					}
				});
				
				ft.play();
			}
		}

		v.setExpanded(false);
		final AbstractLayoutVertex panelRoot = Parameters.stFrame.mainPanel.getPanelRoot();
		LayoutAlgorithm.layout(panelRoot);
		ParallelTransition pt = new ParallelTransition();
		animateRecursive(panelRoot, pt);
		pt.play();

		pt.setOnFinished(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				LayoutEdge.redrawEdges(panelRoot, true);
			}
		});
	}

	private void expanding(AbstractLayoutVertex v)
	{
		Iterator<Node> it = v.getGraphics().getChildren().iterator();
		while(it.hasNext())
		{
			final Node n = it.next();
			if(!n.getClass().equals(Rectangle.class))
			{
				FadeTransition ft = new FadeTransition(Duration.millis(transitionTime), n);
				ft.setToValue(1.0);
				
				ft.setOnFinished(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {		
						n.setVisible(true);
					}
				});
				
				ft.play();
			}
		}

		v.setExpanded(true);
		final AbstractLayoutVertex panelRoot = Parameters.stFrame.mainPanel.getPanelRoot();
		LayoutAlgorithm.layout(panelRoot);
		ParallelTransition pt = new ParallelTransition();
		animateRecursive(panelRoot, pt);
		pt.play();

		pt.setOnFinished(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				LayoutEdge.redrawEdges(panelRoot, true);
			}
		});
	}
	
	private void handlePrimaryDoubleClick(MouseEvent event)
	{
		AbstractLayoutVertex v = (((GUINode)(event.getSource())).getVertex());

		// Collapsing the root vertex leaves us with a blank screen.
		if(!(v instanceof LayoutRootVertex)) {
			if (v.isExpanded())
				collapsing(v);
			else
				expanding(v);
		}
				
		event.consume();
	}

	private void animateRecursive(final AbstractLayoutVertex v, ParallelTransition pt)
	{
		// TODO: Move arrows as well as nodes.
		System.out.println("Size of node " + v.getId() + ": " + v.getWidth() + ", " + v.getHeight());
		System.out.println("Location: " + v.getX() + ", " + v.getY());
		GUINode node = v.getGraphics();
		double newWidth = Parameters.stFrame.mainPanel.scaleX(v.getWidth());
		double newHeight = Parameters.stFrame.mainPanel.scaleY(v.getHeight());
		double currWidth = node.getWidth() * node.getTotalParentScaleX();
		double currHeight = node.getHeight() * node.getTotalParentScaleY();

		double toScaleX = newWidth / currWidth;
		double toScaleY = newHeight / currHeight;
		System.out.println(String.format("Scale X: %.3f", toScaleX));
		System.out.println(String.format("Scale Y: %.3f", toScaleY));

		// Shift to keep upper left corner in the same place after scaling
		System.out.println("Compare widths: " + currWidth + ", " + newWidth);
		System.out.println("Compare heights: " + currHeight + ", " + newHeight);
		double xShift = 0.5 * currWidth * (toScaleX - 1);
		double yShift = 0.5 * currHeight * (toScaleY - 1);
		//double xShift = 0;
		//double yShift = 0;
		System.out.println("Shift: " + xShift + ", " + yShift);
		double toX = Parameters.stFrame.mainPanel.scaleX(v.getX()) + xShift;
		double toY = Parameters.stFrame.mainPanel.scaleY(v.getY()) + yShift;

		if(!(v instanceof LayoutRootVertex)) {
			node.setTotalScaleX(toScaleX * node.getTotalParentScaleX());
			node.setTotalScaleY(toScaleY * node.getTotalParentScaleY());
			if (toScaleX != node.getScaleX() || toScaleY != node.getScaleY()) {
				ScaleTransition st = new ScaleTransition(Duration.millis(transitionTime), node);
				st.setToX(toScaleX);
				st.setToY(toScaleY);
				pt.getChildren().addAll(st);
			}

			if (toX != node.getTranslateX() || toY != node.getTranslateY()) {
				TranslateTransition tt = new TranslateTransition(Duration.millis(transitionTime), node);
				tt.setToX(toX);
				tt.setToY(toY);
				pt.getChildren().addAll(tt);
			}
		}

		Iterator<AbstractLayoutVertex> it = v.getInnerGraph().getVertices().values().iterator();
		while(it.hasNext()){
			AbstractLayoutVertex next = it.next();
			if(v.isExpanded()){
				animateRecursive(next, pt);
			}
		}

	}

	private void handlePrimarySingleClick(MouseEvent event)
	{
		event.consume();
		AbstractLayoutVertex v = ((GUINode)(event.getSource())).getVertex();

		Parameters.stFrame.mainPanel.resetHighlighted(v);
		Parameters.bytecodeArea.setDescription();
		Parameters.setRightText();
	}
}
