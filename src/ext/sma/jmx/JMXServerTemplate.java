package ext.sma.jmx;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;

import org.joda.time.DateTime;
import org.slf4j.Logger;

import com.thingworx.data.util.InfoTableInstanceFactory;
import com.thingworx.entities.utils.ThingUtilities;
import com.thingworx.logging.LogUtilities;
import com.thingworx.metadata.PropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxBaseTemplateDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.system.ContextType;
import com.thingworx.things.Thing;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.BooleanPrimitive;
import com.thingworx.types.primitives.DatetimePrimitive;
import com.thingworx.types.primitives.StringPrimitive;

@ThingworxBaseTemplateDefinition(name = "GenericThing")
public class JMXServerTemplate extends Thing {

	static final String TEMPLATE_NAME = "JMX.ServerTemplate";
	static final String C3P0_MACRO = "_C3P0_";
	static final String C3P0_ROOT = "com.mchange.v2.c3p0:type=PooledDataSource,";
	static final String THINGNAME_MACRO = "_THINGNAME_";
	static final String COMPOSITE_SEP = "_";

	static final String THING_URL_TEMPLATE = "/Thingworx/Composer/index.html#/modeler/details/Thing~_THINGNAME_/properties";

	private String _c3p0PoolName = null;

	private static Logger _logger = LogUtilities.getInstance().getApplicationLogger(JMXServerTemplate.class);

	public JMXServerTemplate() {
	}

	private String getC3p0PoolMBeanName() throws Exception {
		if (_c3p0PoolName == null) {
			ObjectName oname = null;
			final MBeanServer mbs = getMBeanServer();
			final Set<ObjectName> onames = mbs.queryNames(new ObjectName(C3P0_ROOT + "*"), null);
			if (!onames.isEmpty()) {
				oname = onames.iterator().next();
			}
			_c3p0PoolName = oname.toString();
		}
		return _c3p0PoolName;
	}

	private static MBeanServer getMBeanServer() {
		return ManagementFactory.getPlatformMBeanServer();
	}

	JMXMBeanContainerTemplate getContainerByName(String containerName) throws Exception {
		Thing thing = ThingUtilities.findThing(containerName);
		if (thing != null && thing instanceof JMXMBeanContainerTemplate) {
			return (JMXMBeanContainerTemplate) thing;
		} else {
			throw new Exception("MBean Container " + containerName + " not found.");
		}
	}

	static BaseTypes JavaTypeToBaseType(String javaType) {
		switch (javaType) {
		case "java.lang.Double":
		case "double":
		case "java.lang.Float":
		case "float":
			return BaseTypes.NUMBER;
		case "java.lang.Long":
		case "long":
			return BaseTypes.LONG;
		case "java.lang.Short":
		case "java.lang.Integer":
		case "int":
			return BaseTypes.INTEGER;
		case "java.lang.Boolean":
		case "boolean":
			return BaseTypes.BOOLEAN;
		default:
		}
		return BaseTypes.STRING;
	}

	void pushMBeanAttributes(JMXMBeanContainerTemplate container, List<PropertyDefinition> properties)
			throws Exception {

		if (container == null || properties == null)
			return;

		final MBeanServer mbs = getMBeanServer();

		final InfoTable vtqs = InfoTableInstanceFactory.createInfoTableFromDataShape("NamedVTQ");
		final DateTime now = DateTime.now();

		for (PropertyDefinition prop : properties) {

			String name = prop.getName();
			String obj_name = prop.getDescription();
			final BaseTypes type = prop.getBaseType();

			if (C3P0_MACRO.equals(obj_name)) {
				obj_name = getC3p0PoolMBeanName();
				if (obj_name == null) {
					_logger.warn("Could not resolve {} macro.", C3P0_MACRO);
					continue;
				}
			}

			String comp_key = null;
			String attr_name = null;
			int t = name.lastIndexOf(COMPOSITE_SEP);
			if (t > 0) {
				attr_name = name.substring(0, t);
				comp_key = name.substring(t + 1);
			} else {
				attr_name = name;
			}

			try {
				Object attr_value = mbs.getAttribute(new ObjectName(obj_name), attr_name);
				if (comp_key != null && attr_value != null && attr_value instanceof CompositeData) {
					CompositeData cd = (CompositeData) attr_value;
					attr_value = cd.get(comp_key);
				}

				final ValueCollection values = new ValueCollection();
				values.put("name", new StringPrimitive(name));
				values.put("time", new DatetimePrimitive(now));
				values.put("value", BaseTypes.ConvertToPrimitive(attr_value, type));
				vtqs.addRow(values);

			} catch (Exception ex) {
				_logger.warn("Error pushing MBean attribute {} / {} onto {} because {}.", obj_name, name,
						container.getName(), ex.getMessage());
				continue;
			}
		}
		container.UpdatePropertyValues(vtqs);
	}

	@ThingworxServiceDefinition(name = "QueryMBeans", description = "", category = "Jmx:mashup", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "INFOTABLE", aspects = {
			"isEntityDataShape:true", "dataShape:JMX.MBeanInfoDataShape" })
	public InfoTable QueryMBeans(
			@ThingworxServiceParameter(name = "filter", description = "", baseType = "STRING", aspects = {
					"isRequired:false" }) String filter)
			throws Exception {
		final MBeanServer mbs = getMBeanServer();
		final InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape("JMX.MBeanInfoDataShape");

		final Set<ObjectName> onames = mbs.queryNames(filter == null ? null : new ObjectName(filter), null);
		for (ObjectName oname : onames) {
			MBeanInfo bean_info = mbs.getMBeanInfo(oname);
			final ValueCollection values = new ValueCollection();
			values.put("objectName", new StringPrimitive(oname.toString()));
			values.put("className", new StringPrimitive(bean_info.getClassName()));
			values.put("description", new StringPrimitive(bean_info.getDescription()));
			it.addRow(values);
		}
		return it;
	}

	@ThingworxServiceDefinition(name = "QueryMBeansTree", description = "", category = "Jmx:mashup", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "INFOTABLE", aspects = {
			"isEntityDataShape:true", "dataShape:JMX.MBeanTreeDataShape" })
	public InfoTable QueryMBeansTree(
			@ThingworxServiceParameter(name = "filter", description = "", baseType = "STRING", aspects = {
					"isRequired:false" }) String filter)
			throws Exception {
		final MBeanServer mbs = getMBeanServer();
		final Set<ObjectName> onames = mbs.queryNames(filter == null ? null : new ObjectName(filter), null);
		final MBeanTreeBuilder tb = new MBeanTreeBuilder();
		tb.addMBeans(onames);
		return tb.toInfoTable();
	}

	@ThingworxServiceDefinition(name = "GetMBeanAttributesInfo", description = "", category = "Jmx:mashup", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "INFOTABLE", aspects = {
			"isEntityDataShape:true", "dataShape:JMX.MBeanAttributeInfoDataShape" })
	public InfoTable GetMBeanAttributesInfo(
			@ThingworxServiceParameter(name = "mbeanName", description = "", baseType = "STRING", aspects = {
					"isRequired:true" }) String mbeanName,
			@ThingworxServiceParameter(name = "notWritableOnly", description = "", baseType = "BOOLEAN", aspects = {
					"isRequired:true", "defaultValue:true" }) Boolean notWritableOnly,
			@ThingworxServiceParameter(name = "showPreview", description = "", baseType = "BOOLEAN", aspects = {
					"isRequired:true", "defaultValue:false" }) Boolean showPreview)
			throws Exception {

		final InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape("JMX.MBeanAttributeInfoDataShape");

		if (mbeanName == null || mbeanName.isEmpty())
			return it;

		final MBeanServer mbs = getMBeanServer();
		ObjectName oname = new ObjectName(mbeanName);
		final MBeanAttributeInfo attrs[] = mbs.getMBeanInfo(oname).getAttributes();
		for (MBeanAttributeInfo attr_info : attrs) {
			final boolean is_writable = attr_info.isWritable();
			if (notWritableOnly && is_writable) {
				continue;
			}

			boolean isComposite = false;
			final String type = attr_info.getType();
			if ("javax.management.openmbean.CompositeData".equals(type)) {
				Object attr_value = null;
				try {
					attr_value = mbs.getAttribute(oname, attr_info.getName());
				} catch (Exception ex) {
				}

				if (attr_value != null) {
					CompositeData cd = (CompositeData) attr_value;
					CompositeType ct = cd.getCompositeType();
					Set<String> keys = ct.keySet();
					for (String key : keys) {
						Object value = cd.get(key);
						final ValueCollection values = new ValueCollection();
						values.put("name", new StringPrimitive(attr_info.getName() + COMPOSITE_SEP + key));
						values.put("type", new StringPrimitive(ct.getType(key).getClassName()));
						values.put("description", new StringPrimitive(ct.getDescription()));
						values.put("isWritable", new BooleanPrimitive(is_writable));
						values.put("objectName", new StringPrimitive(mbeanName));
						if (showPreview) {
							values.put("preview", new StringPrimitive(value.toString()));
						}
						it.addRow(values);
					}
					isComposite = true;
				} else {
					isComposite = false;
				}

			}
			if (isComposite == false) {
				final ValueCollection values = new ValueCollection();
				values.put("name", new StringPrimitive(attr_info.getName()));
				values.put("type", new StringPrimitive(attr_info.getType()));
				values.put("description", new StringPrimitive(attr_info.getDescription()));
				values.put("isWritable", new BooleanPrimitive(is_writable));
				values.put("objectName", new StringPrimitive(mbeanName));

				if (showPreview) {
					Object attr_value = null;
					try {
						attr_value = mbs.getAttribute(oname, attr_info.getName());
					} catch (Exception ex) {
						attr_value = "[ERROR] - " + ex.getMessage();
					}
					if (attr_value != null) {
						values.put("preview", new StringPrimitive(attr_value.toString()));
					}
				}
				it.addRow(values);
			}
		}
		return it;
	}

	@ThingworxServiceDefinition(name = "CreateMBeanContainer", description = "", category = "Jmx:mashup", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING", aspects = {})
	public void CreateMBeanContainer(
			@ThingworxServiceParameter(name = "containerName", description = "", baseType = "STRING", aspects = {
					"isRequired:true" }) String containerName) {

		JMXMBeanContainerTemplate.createMBeanContainer(containerName, this.getName());
	}

	@ThingworxServiceDefinition(name = "GetMBeanContainers", description = "", category = "Jmx", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "Result", description = "", baseType = "INFOTABLE", aspects = {
			"isEntityDataShape:true", "dataShape: JMX.MBeanContainerList" })
	public InfoTable GetMBeanContainers(
			@ThingworxServiceParameter(name = "viewLinkURL", description = "", baseType = "STRING", aspects = {
					"defaultValue:" + THING_URL_TEMPLATE }) String viewLinkURL)
			throws Exception {

		final InfoTable result = InfoTableInstanceFactory.createInfoTableFromDataShape("JMX.MBeanContainerList");

		InfoTable containers = JMXMBeanContainerTemplate.listMBeanContainers(this.getName());

		for (ValueCollection container : containers.getRows()) {
			final String name = container.getStringValue("name");
			container.put("viewLink", new StringPrimitive(viewLinkURL.replace(THINGNAME_MACRO, name)));
			result.addRow(container);
		}
		return result;
	}

	@ThingworxServiceDefinition(name = "AddMBeanAttributesToContainer", description = "", category = "Jmx:mashup", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING", aspects = {})
	public void AddMBeanAttributesToContainer(
			@ThingworxServiceParameter(name = "containerName", description = "", baseType = "THINGNAME", aspects = {
					"isRequired:true", "thingTemplate:JMX.MBeanContainerTemplate" }) String containerName,
			@ThingworxServiceParameter(name = "attributes", description = "", baseType = "INFOTABLE", aspects = {
					"isRequired:true", "isEntityDataShape:true",
					"dataShape:JMX.MBeanAttributeInfoDataShape" }) InfoTable attributes,
			@ThingworxServiceParameter(name = "logged", description = "", baseType = "BOOLEAN", aspects = {
					"isRequired:true", "defaultValue:false" }) Boolean isLogged)
			throws Exception {

		JMXMBeanContainerTemplate container = getContainerByName(containerName);

		final InfoTable properties = InfoTableInstanceFactory
				.createInfoTableFromDataShape("PropertyDefinitionWithDetails");

		for (ValueCollection attr : attributes.getRows()) {
			final String name = attr.getStringValue("name");
			String mbean = attr.getStringValue("objectName");
			final String type = attr.getStringValue("type");

			if (mbean.startsWith(C3P0_ROOT)) {
				mbean = C3P0_MACRO;
			}

			final ValueCollection values = new ValueCollection();
			values.put("name", new StringPrimitive(name));
			values.put("baseType", new StringPrimitive(JavaTypeToBaseType(type).name()));
			values.put("description", new StringPrimitive(mbean));
			values.put("category", new StringPrimitive(JMXMBeanContainerTemplate.MBEAN_CATEGORY));
			values.put("isLogged", new BooleanPrimitive(isLogged));
			values.put("isReadOnly", new BooleanPrimitive(false));
			values.put("isPersistent", new BooleanPrimitive(false));
			values.put("isRemote", new BooleanPrimitive(false));
			// values.put("remotePropertyName", new StringPrimitive(name));
			// values.put("timeout", new IntegerPrimitive((Number) 2));
			properties.addRow(values);
		}
		container.AddPropertyDefinitions(properties, true);
	}

	@ThingworxServiceDefinition(name = "GetMBeanContainerAttributesInfo", description = "", category = "Jmx:mashup", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "INFOTABLE", aspects = {
			"isEntityDataShape:true", "dataShape:PropertyDefinition" })
	public InfoTable GetMBeanContainerAttributesInfo(
			@ThingworxServiceParameter(name = "containerName", description = "", baseType = "STRING", aspects = {
					"isRequired:true" }) String containerName)
			throws Exception {

		if (containerName == null || containerName.isEmpty())
			return InfoTableInstanceFactory.createInfoTableFromDataShape("PropertyDefinition");

		JMXMBeanContainerTemplate container = getContainerByName(containerName);
		return container.GetMBeanPropertyDefinitions();
	}

	@ThingworxServiceDefinition(name = "ResetC3p0Bean", description = "", category = "Jmx", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "Result", description = "", baseType = "STRING", aspects = {})
	public String ResetC3p0Bean() throws Exception {
		_logger.trace("Entering Service: ResetC3p0Bean");
		_c3p0PoolName = null;
		_logger.trace("Exiting Service: ResetC3p0Bean");
		return this.getC3p0PoolMBeanName();
	}
	
}
