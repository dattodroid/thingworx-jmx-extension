package ext.sma.jmx;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.json.JSONObject;
import org.slf4j.Logger;

import com.thingworx.entities.utils.EntityUtilities;
import com.thingworx.entities.utils.ThingUtilities;
import com.thingworx.logging.LogUtilities;
import com.thingworx.metadata.PropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxBaseTemplateDefinition;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinitions;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.relationships.RelationshipTypes.ThingworxRelationshipTypes;
import com.thingworx.resources.entities.EntityServices;
import com.thingworx.resources.queries.Searcher;
import com.thingworx.things.Thing;
import com.thingworx.things.properties.ThingProperty;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.TagCollection;
import com.thingworx.types.primitives.IPrimitiveType;
import com.thingworx.types.primitives.structs.Location;
import com.thingworx.types.primitives.structs.ThingCode;
import com.thingworx.types.primitives.structs.Vec2;
import com.thingworx.types.primitives.structs.Vec3;
import com.thingworx.types.primitives.structs.Vec4;

@ThingworxPropertyDefinitions(properties = {
		@ThingworxPropertyDefinition(name = JMXMBeanContainerTemplate.SERVER_PROPERTY, description = "", category = "", baseType = "THINGNAME", isLocalOnly = false, aspects = {
				"isPersistent:true", "dataChangeType:NEVER", "thingTemplate:JMX.ServerTemplate" }) })

@ThingworxBaseTemplateDefinition(name = "GenericThing")
public class JMXMBeanContainerTemplate extends Thing {

	static final String TEMPLATE_NAME = "JMX.MBeanContainerTemplate";
	static final String SERVER_PROPERTY = "JmxServer";
	static final String MBEAN_CATEGORY = "mbean:attr";
	static final String DESCRIPTION = "Container for JMX MBean attributes";
	static final String TAG_VOCAB = "Jmx";
	static final String TAG_TERM = "Container";
	static final String THINGNAME_PREFIX = "jmx.";

	private static Logger _logger = LogUtilities.getInstance().getApplicationLogger(JMXMBeanContainerTemplate.class);

	public JMXMBeanContainerTemplate() {
		// TODO Auto-generated constructor stub
	}

	private JMXServerTemplate getJMXServer() throws Exception {
		String server_name = getProperty(SERVER_PROPERTY).getValue().getStringValue();
		Thing thing = ThingUtilities.findThing(server_name);
		if (thing != null && thing instanceof JMXServerTemplate) {
			return (JMXServerTemplate) thing;
		} else {
			throw new Exception("JMX Server " + server_name + " not found.");
		}
	}

	private boolean isCandiateForUpdate(ThingProperty property, boolean ignoreCache) {

		final PropertyDefinition prop_def = property.getPropertyDefinition();
		if (prop_def.isBuiltIn() || ! MBEAN_CATEGORY.equals(prop_def.getCategory())) {
			return false;
		}

		boolean demandRead = false;

		if (ignoreCache) {
			demandRead = true;
		} else {
			final Integer cacheTime = prop_def.getCacheTime();
			
			/* PSPT-68791 - workaround
			 *  0 / null : Pull every read
			 *  < 0 : Read from cache
			 *  > 0 : Read from cache or Pull if older than ...
			 */
			
			if (cacheTime == null || cacheTime == 0) {
				demandRead = true;
			}
			else {
				if (cacheTime < 0) {
					demandRead = false;
				} else if (cacheTime > 0) {
					final long currentTime = System.currentTimeMillis();
					final long lastTime = property.getTime().getMillis();
					final long diff = currentTime - lastTime;

					if (diff > cacheTime) {
						demandRead = true;
					}
				}
			}
		}
		return demandRead;
	}

	static void createMBeanContainer(String containerName, String serverName) {
	
		EntityServices entityService = (EntityServices) EntityUtilities.findEntity("EntityServices",
				ThingworxRelationshipTypes.Resource);
		try {
	
			TagCollection tags = new TagCollection();
			tags.AddTag("Jmx", "Container");
			
			if (!containerName.startsWith(THINGNAME_PREFIX)) {
				containerName = THINGNAME_PREFIX + containerName;
			}
			
			entityService.CreateThing(containerName, "Container for JMX MBeans attributes", tags, TEMPLATE_NAME);
			Thing thing = ThingUtilities.findThing(containerName);
			thing.EnableThing();
			thing.RestartThing();
			thing.setPropertyValue(SERVER_PROPERTY, BaseTypes.ConvertToPrimitive(serverName, BaseTypes.THINGNAME));
		} catch (Exception e) {
			_logger.error("Exception: {} occurred while creating new Thing: {}", e, containerName);
			try {
				entityService.DeleteThing(containerName);
			} catch (Exception e1) {
				_logger.error("Exception: {} occurred while deleting Thing: {}", e, containerName);
			}
		}
	}

	static InfoTable listMBeanContainers(String serverName) throws Exception {
		Searcher searchFunctions = (Searcher) EntityUtilities.findEntity("SearchFunctions",
				ThingworxRelationshipTypes.Resource);
		JSONObject query = new JSONObject("{\"filters\": { \"type\": \"EQ\", \"fieldName\": \"" + SERVER_PROPERTY
				+ "\",\"value\": \"" + serverName + "\"}}");
		return searchFunctions.SearchThingsByTemplate(TEMPLATE_NAME, 100.0, null, null, query);
	}

	List<PropertyDefinition> getMBeanPropertiesDefinitionForUpdate(boolean force) {
		return getProperties().values()
				.stream()
				.filter(p -> isCandiateForUpdate(p, force))
				.map(p -> p.getPropertyDefinition())
				.collect(Collectors.toList());
	}
	
	List<ThingProperty> getLoggedMBeanProperties() {
		return getProperties().values()
				.stream()
				.filter(p -> 
					p.getPropertyDefinition().isLogged() && MBEAN_CATEGORY.equals(p.getPropertyDefinition().getCategory()))
				.collect(Collectors.toList());
	}
	
	private void addPropertyToValueStream(ThingProperty property, DateTime timestamp) throws Exception {
		
		if (timestamp == null) {
			timestamp = property.getTime();
		}

		String prop_name = property.getPropertyDefinition().getName();
		IPrimitiveType value = property.getValue();
		
		switch (value.getBaseType()) {
		case STRING:
			AddStringValueStreamEntry(timestamp, prop_name, (String) value.getValue());
			break;
		case BOOLEAN:
			this.AddBooleanValueStreamEntry(timestamp, prop_name, (Boolean) value.getValue());
			break;
		case DATETIME:
			this.AddDateTimeValueStreamEntry(timestamp, prop_name, (DateTime) value.getValue());
			break;
		case IMAGE:	
			this.AddImageValueStreamEntry(timestamp, prop_name, (byte[]) value.getValue());
			break;
		case INFOTABLE:	
			this.AddInfoTableValueStreamEntry(timestamp, prop_name, (InfoTable) value.getValue());
			break;
		case INTEGER:	
			this.AddIntegerValueStreamEntry(timestamp, prop_name,  (Integer) value.getValue());
			break;
		case LOCATION:			
			this.AddLocationValueStreamEntry(timestamp, prop_name,  (Location) value.getValue());
			break;
		case LONG:
			this.AddLongValueStreamEntry(timestamp, prop_name,  (Long) value.getValue());
			break;
		case NUMBER:
			this.AddNumberValueStreamEntry(timestamp, prop_name,  (Double) value.getValue());
			break;
		case THINGCODE:	
			this.AddThingCodeValueStreamEntry(timestamp, prop_name, (ThingCode) value.getValue());
			break;
		case VEC2:		
			this.AddVec2ValueStreamEntry(timestamp, prop_name, (Vec2) value.getValue());
			break;
		case VEC3:	
			this.AddVec3ValueStreamEntry(timestamp, prop_name, (Vec3) value.getValue());
			break;
		case VEC4:	
			this.AddVec4ValueStreamEntry(timestamp, prop_name, (Vec4) value.getValue());
			break;
		default:
		}
	}

	@Override
	public void checkDemandRead(ThingProperty property) throws Exception {

		if (isCandiateForUpdate(property, false)) {
			final JMXServerTemplate server = getJMXServer();
			server.pushMBeanAttributes(this, Arrays.asList(property.getPropertyDefinition()));
		}
		super.checkDemandRead(property);
	}
	
	@ThingworxServiceDefinition(name = "AddMBeanPropertyDefinition", description = "", category = "Jmx", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING", aspects = {})
	public void AddMBeanPropertyDefinition(
			@ThingworxServiceParameter(name = "mbeanName", description = "", baseType = "STRING", aspects = {
					"isRequired:true" }) String mbeanName,
			@ThingworxServiceParameter(name = "attributeName", description = "", baseType = "STRING", aspects = {
					"isRequired:true" }) String attributeName,
			@ThingworxServiceParameter(name = "type", description = "", baseType = "BASETYPENAME", aspects = {
					"isRequired:true" }) String type)
			throws Exception {

		AddPropertyDefinition(attributeName, mbeanName, type, MBEAN_CATEGORY, null, false, false, false, null, null,
				false, null, null, null, null, null, null);
	}

	@ThingworxServiceDefinition(name = "GetMBeanPropertyDefinitions", description = "", category = "Jmx", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "INFOTABLE", aspects = {
			"isEntityDataShape:true", "dataShape:PropertyDefinition" })
	public InfoTable GetMBeanPropertyDefinitions() throws Exception {
		return GetPropertyDefinitions(MBEAN_CATEGORY, null, null);
	}

	@ThingworxServiceDefinition(name = "RefreshMBeanAttributes", description = "", category = "Jmx", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING", aspects = {})
	public void RefreshMBeanAttributes(
			@ThingworxServiceParameter(name = "ignoreCache", description = "", baseType = "BOOLEAN", aspects = {
					"isRequired:true", "defaultValue:false" }) Boolean ignoreCache)
			throws Exception {

		final JMXServerTemplate server = getJMXServer();
		server.pushMBeanAttributes(this, getMBeanPropertiesDefinitionForUpdate(ignoreCache));
	}

	@ThingworxServiceDefinition(name = "WriteMBeanPropertiesToValueStream", description = "", category = "", isAllowOverride = false, aspects = {
			"isAsync:true" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING", aspects = {})
	public void WriteMBeanPropertiesToValueStream(
			@ThingworxServiceParameter(name = "forceRefresh", description = "", baseType = "BOOLEAN", aspects = {
					"defaultValue:false" }) Boolean forceRefresh) throws Exception {

		if (forceRefresh) {
			RefreshMBeanAttributes(true);
		}
		
		final DateTime now = DateTime.now();
		List<ThingProperty> properties = getLoggedMBeanProperties();
		for (ThingProperty property : properties) {
			this.addPropertyToValueStream(property, now);
		}
	}
}
