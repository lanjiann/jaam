package org.ucombinator.jaam.visualizer.gui;

import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.BorderLayout;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.Component;

import org.ucombinator.jaam.visualizer.layout.AbstractLayoutVertex;
import org.ucombinator.jaam.visualizer.main.Parameters;

public class SearchResults extends JPanel
{
    public JTree searchTree;
    private DefaultMutableTreeNode root;
    public static int nodeHeight = 40;
    
	public SearchResults()
	{
        this.setLayout(new BorderLayout());
        
        this.root = new DefaultMutableTreeNode("Search Results");
        this.searchTree = new JTree(root);
        this.searchTree.setShowsRootHandles(true);
        this.searchTree.setRootVisible(false);
        this.searchTree.setRowHeight(SearchResults.nodeHeight);
        this.searchTree.setCellRenderer(new SearchRenderer());

        this.add(this.searchTree, BorderLayout.CENTER);
        /*this.searchTree.addMouseListener
		(
			new MouseListener()
			{
				public void mouseClicked(MouseEvent e)
				{
				    //System.out.println("Location: " + e.getX() + ", " + e.getY());
				    //System.out.println("Search tree: " + searchTree.toString());
                    TreePath path = searchTree.getPathForLocation(e.getX(), e.getY());
                    //System.out.println("TreePath = " + path);
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode)(path.getLastPathComponent());
                    org.ucombinator.jaam.visualizer.graph.AbstractVertex ver = (org.ucombinator.jaam.visualizer.graph.AbstractVertex)(node.getUserObject());
                    
                    if(!ver.isVisible)
                        return;

					if(e.isShiftDown())
					{
                        if(ver.isSelected())
                        {
                            ver.clearAllSelect();
                        }
                        else
                        {
                            Parameters.vertexHighlight = true;
                            ver.addHighlight(true, false, true, true);
                        }
					}
					else
					{
                        Main.graph.clearSelects();
                        Parameters.vertexHighlight = true;
                        ver.addHighlight(true, false, true, true);
					}
					
					Main.graph.redoCycleHighlights();
					Parameters.repaintAll();
				}
				
				public void mousePressed(MouseEvent e){}
				
				public void mouseReleased(MouseEvent e){}
				
				public void mouseEntered(MouseEvent e){}
				
				public void mouseExited(MouseEvent e){}
			}
		);*/
 	}

	//Set the text for the area
	public void writeText()
	{
        this.root.removeAllChildren();
        if(Parameters.stFrame.mainPanel.highlighted.size() > 0) {
            // We don't want to include the panel root, so we start our check with its children
            for(AbstractLayoutVertex v : Parameters.stFrame.mainPanel.getPanelRoot().getInnerGraph().getVertices().values())
                v.addTreeNodes(this.root);

            // TODO: Auto-expand nodes?
            DefaultTreeModel model = (DefaultTreeModel)this.searchTree.getModel();
            model.reload(this.root);
        }
	}
    
    /*public void fixCaretPosition()
    {
        Rectangle window = this.searchTree.getVisibleRect();
        int first = this.searchTree.getClosestRowForLocation(window.x, window.y + SearchResults.nodeHeight);
        int last  = this.searchTree.getClosestRowForLocation(window.x, window.y + window.height - SearchResults.nodeHeight);
        
        if(first < 0)
            return;
        
        DefaultMutableTreeNode node;
        AbstractLayoutVertex ver;
        
        for(int i = first; i <= last; i++)
        {
            node = (DefaultMutableTreeNode)(this.searchTree.getPathForRow(i).getLastPathComponent());
            ver = (AbstractLayoutVertex) node.getUserObject();
            if(ver.isSelected())
                return;
        }
        
        for(int i = first - 1, j = last + 1; i >= 0 || j < this.searchTree.getRowCount(); i--, j++)
        {
            if (i >= 0)
            {
                node = (DefaultMutableTreeNode)(this.searchTree.getPathForRow(i).getLastPathComponent());
                ver = (AbstractLayoutVertex) node.getUserObject() ;
                if (ver.isSelected())
                {
                    this.searchTree.scrollRowToVisible(i);
                    return;
                }
            }
            if (j < this.searchTree.getRowCount())
            {
                node = (DefaultMutableTreeNode)(this.searchTree.getPathForRow(j).getLastPathComponent());
                ver = (AbstractLayoutVertex) node.getUserObject();
                if (ver.isSelected())
                {
                    this.searchTree.scrollRowToVisible(j);
                    return;
                }
            }
        }
    }*/
    
    private class SearchRenderer extends DefaultTreeCellRenderer
    {
        public Component getTreeCellRendererComponent(JTree tree, Object obj, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus)
        {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, obj, selected, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) obj;

            if (node == root)
                return label;
            AbstractLayoutVertex ver = (AbstractLayoutVertex) node.getUserObject();

            label.setText(ver.getShortDescription());
            label.setFont(Parameters.font);
            label.setOpaque(true);
            /*if (ver.isSelected())
            {
                label.setBackground(Parameters.colorHighlight);
                label.setForeground(Color.BLACK);
            }
            else*/ if (ver.isHighlighted)
            {
                label.setBackground(Color.WHITE);
                label.setForeground(Color.BLACK);
            }
            else
            {
                label.setBackground(Color.WHITE);
                label.setForeground(Color.GRAY);
            }
            return label;
        }
    }
}
