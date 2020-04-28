package ext.sma.jmx;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

import com.thingworx.data.util.InfoTableInstanceFactory;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.IntegerPrimitive;
import com.thingworx.types.primitives.StringPrimitive;

public class MBeanTreeBuilder {
	
	private int _id = 1;
	private final Map<String, Node> _tree;
	
	MBeanTreeBuilder() {
		_tree = new HashMap<String, Node>();
	}
	
	void addMBeans(Set<ObjectName> objectNames) {
		for (ObjectName oname : objectNames) {
			addMbean(oname.toString());
		}
	}
	
	private int nextId() {
		return _id++;
	}
	
	private Node addMbean(String objectName) {
		String path = objectName.replace(":", ",");
		Node node = _tree.get(path);
		if (node == null) {
			String[] name_and_parent = splitPath(path);
			Node parent = getOrCreateParent(name_and_parent[1]);
			node = new Node(nextId(), parent.getId(), formatName(name_and_parent[0]), objectName);
			_tree.put(path, node);
		}
		
		return node;
	}
	
	private Node getOrCreateParent(String parentPath) {
		Node node = _tree.get(parentPath);
		if (node == null) {
			String[] name_and_parent = splitPath(parentPath);
			int parent_id = 0;
			if (name_and_parent[1] != null) {
				Node parent = getOrCreateParent(name_and_parent[1]);
				parent_id = parent.getId();
			}
			
			node = new Node(nextId(), parent_id, formatName(name_and_parent[0]), "");
			_tree.put(parentPath, node);
		}
		
		return node;
	}
	
	static private String formatName(String name) {
		int c = name.lastIndexOf('=');
		return name.substring(c+1);
	}
	
	static private String[] splitPath(String path) {
		String name;
		String parent_path;
		int c = path.lastIndexOf(',');
		if (c > 0) {
			parent_path = path.substring(0, c);
			name = path.substring(c+1);
		}
		else {
			name = path;
			parent_path = null;
		}
			
		String[] np = {name, parent_path};
		return np;
	}

	public InfoTable toInfoTable() throws Exception {
		InfoTable it  = InfoTableInstanceFactory.createInfoTableFromDataShape("JMX.MBeanTreeDataShape");
		_tree.forEach((path, node) -> {
			final ValueCollection values = new ValueCollection();
			values.put("nodeId", new StringPrimitive(Integer.toString(node.getId())));
			values.put("parentId", new StringPrimitive(Integer.toString(node.getParentId())));
			values.put("nodeName", new StringPrimitive(node.getName()));
			values.put("objectName", new StringPrimitive(node.getObjectName()));
			it.addRow(values);
		});	
				
		return it;
	}
	
	
	private class Node implements Comparable<Node> {
		private final int id;
		private final int parent_id;
		private final String name;
		private final String objectName;
		
		protected Node(int id, int parent_id, String name, String objectName) {
			this.id = id;
			this.parent_id = parent_id;
			this.name = name;
			this.objectName = objectName;
		}

		protected int getId() {
			return id;
		}

		protected int getParentId() {
			return parent_id;
		}

		protected String getName() {
			return name;
		}

		protected String getObjectName() {
			return objectName;
		}	
		
		public String toString() {
			return id + ", " + parent_id + ", " + name + ", " + objectName; 
		}
		
	    @Override
	    public int compareTo(Node node) {
	        return (int)(this.id - node.getId());
	    }
	}
}
