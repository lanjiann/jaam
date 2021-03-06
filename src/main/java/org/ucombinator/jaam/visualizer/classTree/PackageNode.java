package org.ucombinator.jaam.visualizer.classTree;

import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeItem;
import javafx.scene.paint.Color;
import org.controlsfx.glyphfont.FontAwesome;
import org.ucombinator.jaam.visualizer.controllers.CodeViewController;
import org.ucombinator.jaam.visualizer.layout.MethodEntity;
import org.ucombinator.jaam.visualizer.state.StateVertex;
import org.ucombinator.jaam.visualizer.main.Main;

import java.util.HashSet;

public class PackageNode extends ClassTreeNode implements Comparable<PackageNode>{

    private String path;
    private String shortName;

    public HashSet<PackageNode> subPackages;
    public HashSet<ClassNode>   classNodes; // Leaf nodes store their associated methods11

    public PackageNode(String name, String prefix) {
        this.path = prefix;
        this.shortName = name;

        this.subPackages = new HashSet<>();
        this.classNodes  = new HashSet<>();
    }

    @Override
    public String toString() {
        return shortName;
    }

    public PackageNode addPackageIfAbsent(String name) {
        PackageNode subDir = null;
        for (PackageNode f : subPackages) {
            if (f.shortName.compareTo(name) == 0) {
                subDir = f;
                break;
            }
        }
        if (subDir == null) {
            subDir = new PackageNode(name, this.getName());
            subPackages.add(subDir);
        }

        return subDir;
    }

    @Override
    public String getName() {
        if(path.compareTo("") == 0) {
            return shortName;
        }
        else {
            return path + "." + shortName;
        }
    }

    public String getShortName() {
        return shortName;
    }

    public void addClassIfAbsent(String name) {
        classNodes.add(new ClassNode(this.getName(), name));
    }

    public void compress()
    {
        while(subPackages.size() == 1 && classNodes.isEmpty())
        {
            PackageNode onlyElement = subPackages.iterator().next();
            shortName = shortName.concat("." + onlyElement.shortName);
            subPackages = onlyElement.subPackages;
            classNodes.addAll(onlyElement.classNodes);
        }

        for(PackageNode f : subPackages)
            f.compress();
    }

    public void build(CheckBoxTreeItem<ClassTreeNode> parent) {
        CheckBoxTreeItem<ClassTreeNode> item = buildTreeItem(parent);

        item.setGraphic(this.getGraphic());

        subPackages.stream().sorted().forEach(p -> p.build(item));

        classNodes.stream().sorted().forEach(c -> c.build(item));
    }

    @Override
    public void handleDoubleClick(CodeViewController codeView) { /* do nothing */ }

    @Override
    public HashSet<StateVertex> getChildVertices()
    {
        HashSet<StateVertex> all = new HashSet<>();
        for (PackageNode p : subPackages) {
            all.addAll(p.getChildVertices());
        }
        for (ClassNode c : classNodes) {
            all.addAll(c.getChildVertices());
        }
        return all;
    }

    public boolean addVertex(StateVertex vertex) {

        if (!(vertex instanceof MethodEntity)) return false;

        String vertexClassName = ((MethodEntity) vertex).getClassName();

        for (PackageNode p : subPackages) {
            if (vertexClassName.startsWith(p.getName())) {
                return p.addVertex(vertex);
            }
        }
        for (ClassNode c : classNodes) {
            if (vertexClassName.startsWith(c.getName())) {
                return c.addVertex(vertex);
            }
        }
        return false;
    }

    public void addFields(CodeViewController codeViewController) {
        for (PackageNode p : subPackages) {
            p.addFields(codeViewController);
        }

        for (ClassNode c : classNodes) {
            c.addFields(codeViewController);
        }
    }

    @Override
    public int compareTo(PackageNode packageNode) {
        return this.getShortName().compareTo(packageNode.getShortName());
    }

    @Override
    public Node getGraphic() {
        return Main.getIconFont().create(FontAwesome.Glyph.FOLDER).color(Color.DARKGRAY);
    }
}
