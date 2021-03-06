/**
 * Created Oct 30, 2015 
 * Copyright Motion Picture Laboratories, Inc. 2015
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of 
 * this software and associated documentation files (the "Software"), to deal in 
 * the Software without restriction, including without limitation the rights to use, 
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of 
 * the Software, and to permit persons to whom the Software is furnished to do so, 
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all 
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS 
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR 
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER 
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.movielabs.mddflib.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;

import com.movielabs.mddf.MddfContext;
import com.movielabs.mddflib.logging.LogMgmt;
import com.movielabs.mddflib.logging.LogReference;
import com.movielabs.mddflib.util.xml.RatingSystem;
import com.movielabs.mddflib.util.xml.SchemaWrapper;
import com.movielabs.mddflib.util.xml.StructureValidation;
import com.movielabs.mddflib.util.xml.XsdValidation;
import com.movielabs.mddflib.util.xml.XmlIngester;

/**
 * Base class for implementation of validators of a XML file for conformance
 * with the <tt>Common Metadata (md)</tt> specification as defined in
 * <tt>TR-META-CM (v2.4)</tt>. Extending subclasses will provide additional
 * customization to check for conformance with specifications built on top of
 * the CM (e.g., as conforming to the Common Media Manifest (CMM) as specified
 * in <tt>TR-META-MMM (v1.5)</tt>).
 * 
 * 
 * @author L. Levin, Critical Architectures LLC
 *
 */
public class CMValidator extends XmlIngester {

	/**
	 * Used to facilitate keeping track of cross-references and identifying
	 * 'orphan' elements.
	 * 
	 * @author L. Levin, Critical Architectures LLC
	 *
	 */
	protected class XrefCounter {
		private int count = 0;
		private String elType;
		private String elId;

		/**
		 * @param elType
		 * @param elId
		 */
		XrefCounter(String elType, String elId) {
			super();
			this.elType = elType;
			this.elId = elId;
		}

		int increment() {
			count++;
			return count;
		}

		void validate() {
			if (count > 0) {
				return;
			} else {
				Map<String, Element> idMap = id2XmlMappings.get(elType);
				Element targetEl = idMap.get(elId);
				String explanation = "The element is never referenced by it's ID";
				String msg = "Unreferenced <" + elType + "> Element";
				logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_WARN, targetEl, msg, explanation, null, logMsgSrcId);
			}
		}

	}

	public static final String LOGMSG_ID = "AbstractValidator";

	protected static HashMap<String, String> id2typeMap;

	protected static Properties iso3166_1_codes;

	protected static HashSet<String> specialRatings = new HashSet<String>();

	private static JSONArray iso639_2;

	private static JSONArray iso639_3;

	private static JSONArray unM49;

	private static JSONArray rfc5646Variant;

	private static JSONArray rfc5646Script;

	static {
		specialRatings.add("ALL");
		specialRatings.add("UNRATED");
		specialRatings.add("ADULT");
		specialRatings.add("PROSCRIBED");
		try {
			/*
			 * Language codes are in their own file
			 */
			JSONObject jsonRsrc = getMddfResource("rfc5646");
			JSONObject rfc5646 = jsonRsrc.getJSONObject("rfc5646");
			iso639_2 = rfc5646.getJSONArray("iso639-2");
			iso639_3 = rfc5646.getJSONArray("iso639-3");
			unM49 = rfc5646.getJSONArray("UN-M49");
			rfc5646Variant = rfc5646.getJSONArray("variant");
			rfc5646Script = rfc5646.getJSONArray("script");
			/*
			 * ISO region/country codes are simple so we use Properties
			 */
			String iso3166RsrcPath = MddfContext.RSRC_PATH + "ISO3166-1.properties";
			iso3166_1_codes = loadProperties(iso3166RsrcPath);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	protected Namespace rootNS;

	protected boolean validateC;
	protected Element curRootEl;
	/**
	 * Set to <tt>true</tt> when starting validation of a file, then set to
	 * false when any error is detected.
	 */
	protected boolean curFileIsValid;
	protected Map<String, HashSet<String>> idSets;
	protected Map<String, Map<String, XrefCounter>> idXRefCounts;
	protected Map<String, Map<String, Element>> id2XmlMappings;

	protected XsdValidation xsdHelper;

	protected StructureValidation structHelper;

	/**
	 * @param loggingMgr
	 */
	public CMValidator(LogMgmt loggingMgr) {
		super(loggingMgr);
		xsdHelper = new XsdValidation(loggingMgr);
		structHelper = new StructureValidation(this, logMsgSrcId);
	}

	/**
	 * @param validateC
	 */
	public CMValidator(boolean validateC, LogMgmt loggingMgr) {
		this(loggingMgr);
		this.validateC = validateC;
		logMsgSrcId = LOGMSG_ID;
	}

	/**
	 * Validate everything that is not fully specified via the XSD.
	 */
	protected void validateConstraints() {
		idSets = new HashMap<String, HashSet<String>>();
		idXRefCounts = new HashMap<String, Map<String, XrefCounter>>();
		id2XmlMappings = new HashMap<String, Map<String, Element>>();

	}

	/**
	 * Check for consistent usage. This typically means that an OPTIONAL element
	 * will be either REQUIRED or INVALID for certain use-cases (e.g.
	 * BundledAsset is only allowed when WorkType is 'Collection').
	 * <p>
	 * Validation is based primarily on the <i>structure definitions</i> defined
	 * in a version-specific JSON file. This will define various criteria that
	 * must be satisfied for a given usage. The criteria are specified in the
	 * form of XPATHs.
	 * </p>
	 * 
	 * @return
	 * @see com.movielabs.mddflib.util.xml.StructureValidation
	 */
	protected void validateUsage() {
		/*
		 * nothing to deo Should be overridden by extending classes.
		 */
	}

	/**
	 * 
	 */
	private void validateCardinality() {
		/*
		 * Sec 4.2.7: At least one instance of ContainerReference or
		 * BasicMetadata must be included for each Inventory/Metadata instance
		 */

	}

	/**
	 * @param elementName
	 * @param idxAttribute
	 * @param parentName
	 */
	protected void validateIndexing(String elementName, String idxAttribute, String parentName) {
		XPathExpression<Element> xpExpression = xpfac.compile(".//manifest:" + parentName, Filters.element(), null,
				manifestNSpace);
		List<Element> parentElList = xpExpression.evaluate(curRootEl);
		for (int i = 0; i < parentElList.size(); i++) {
			Element parentEl = (Element) parentElList.get(i);
			List<Element> childList = parentEl.getChildren(elementName, manifestNSpace);
			Boolean[] validIndex = new Boolean[childList.size()];
			Arrays.fill(validIndex, Boolean.FALSE);
			for (int cPtr = 0; cPtr < childList.size(); cPtr++) {
				Element nextChildEl = childList.get(cPtr);
				/*
				 * Each child Element must have @index attribute
				 */
				String indexAsString = nextChildEl.getAttributeValue(idxAttribute);
				if (!isValidIndex(indexAsString)) {
					String msg = "Invalid value for indexing attribute (non-negative integer required)";
					logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_ERR, nextChildEl, msg, null, null, logMsgSrcId);
					curFileIsValid = false;
				} else {
					int index = Integer.parseInt(indexAsString);
					if (index >= validIndex.length) {
						String msg = "value for indexing attribute out of range";
						logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_ERR, nextChildEl, msg, null, null, logMsgSrcId);

					} else if (validIndex[index]) {
						// duplicate value
						String msg = "Invalid value for indexing attribute (duplicate value)";
						logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_ERR, nextChildEl, msg, null, null, logMsgSrcId);
						curFileIsValid = false;
					} else {
						validIndex[index] = true;
					}
				}
			}
			// now make sure all values were covered..
			boolean allValues = true;
			for (int j = 0; j < validIndex.length; j++) {
				allValues = allValues && validIndex[j];
			}
			if (!allValues) {
				String msg = "Invalid indexing of " + elementName + " sequence: be monotonically increasing";
				logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_ERR, parentEl, msg, null, null, logMsgSrcId);
				curFileIsValid = false;
			}
		}
	}

	/**
	 * Check all Elements (or attributes) that are REQUIRED by a MDDF
	 * Specification to provide a value actually do. This is a necessary step as
	 * validation against the XSD only verify the presence of an Element. Thus,
	 * an XML file with the Element <tt>&lt;Foo&gt;&lt;/Foo&gt;</tt> would pass
	 * the XSD check even though it fails to provide a required value.
	 * 
	 * @param targetSchema
	 */
	protected void validateNotEmpty(SchemaWrapper targetSchema) {
		List<XPathExpression<?>> reqElXpList = targetSchema.getReqElList();
		for (XPathExpression<?> xpExp : reqElXpList) {
			List<?> elementList = xpExp.evaluate(curRootEl);
			for (Object next : elementList) {
				String value = null;
				String label = null;
				Element targetEl = null;
				if (next instanceof Element) {
					targetEl = (Element) next;
					value = targetEl.getTextNormalize();
					label = targetEl.getName();
				} else if (next instanceof Attribute) {
					Attribute targetAtt = (Attribute) next;
					value = targetAtt.getValue();
					targetEl = targetAtt.getParent();
					label = targetEl.getName() + "->" + targetAtt.getName();
				}

				if ((value == null) || (value.isEmpty())) {
					String msg = label + " not specified. A value must be provided";
					logIssue(logMsgDefaultTag, LogMgmt.LEV_ERR, targetEl, msg, null, null, logMsgSrcId);
					curFileIsValid = false;
				}
			}
		}
	}

	// ..................

	/**
	 * Check for the presence of an ID and, if provided, verify it is unique and
	 * has the correct structure and syntax as defined in Section 3 of
	 * <tt>Manifest/Avails Delivery Best Practices (BP-META-MMMD)</tt>. All
	 * extracted ID values will be saved as an <tt>idSet</tt> that can latter be
	 * used to validate cross-references.
	 * <p>
	 * The value of the ID is extracted using the <tt>idElement</tt> and
	 * (optional) <tt>idAttribute</tt> arguments. If the <tt>idAttribute</tt> is
	 * <tt>null</tt> the value used is the idElement's text.
	 * </p>
	 * 
	 * @param idElement
	 * @param idAttribute
	 *            (optional)
	 * @param reqUniqueness
	 * @param chkSyntax
	 * @return the <tt>Set</tt> of unique IDs found.
	 */
	protected HashSet<String> validateId(String idElement, String idAttribute, boolean reqUniqueness,
			boolean chkSyntax) {
		XPathExpression<Element> xpExpression = xpfac.compile(".//" + rootNS.getPrefix() + ":" + idElement,
				Filters.element(), null, rootNS);
		HashSet<String> idSet = new HashSet<String>();

		/*
		 * idXRefCounter is initialized here but will 'filled in' by
		 * validateXRef();
		 */
		HashMap<String, XrefCounter> idXRefCounter = new HashMap<String, XrefCounter>();
		/*
		 * id2XmlMap is provided for look-ups later as an alternative to using
		 * XPaths.
		 */
		HashMap<String, Element> id2XmlMap = new HashMap<String, Element>();

		List<Element> elementList = xpExpression.evaluate(curRootEl);
		for (int i = 0; i < elementList.size(); i++) {
			/*
			 * XSD may specify ID attribute as OPTIONAL but we need to verify
			 * cross-references and uniqueness.
			 */
			Element targetEl = (Element) elementList.get(i);
			if (targetEl.getParentElement().getName().equals("Audiovisual")) {
				// special case... do nothing
				continue;
			}
			String idValue = null;
			String idKey = null;
			if (idAttribute == null) {
				idValue = targetEl.getTextNormalize();
				idKey = idElement;
			} else {
				idValue = targetEl.getAttributeValue(idAttribute);
				idKey = idAttribute;
			}
			id2XmlMap.put(idValue, targetEl);
			XrefCounter count = new XrefCounter(idElement, idValue);
			idXRefCounter.put(idValue, count);

			if ((idValue == null) || (idValue.isEmpty())) {
				String srcLabel = null;
				String targetLabel = null;
				if (idAttribute != null) {
					srcLabel = idElement + "@" + idAttribute;
					targetLabel = idElement;
				} else {
					targetLabel = targetEl.getParentElement().getName();
					srcLabel = targetLabel + "/" + idElement;
				}
				String msg = srcLabel + " not specified. References to this " + targetLabel
						+ " will not be supportable.";
				logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_WARN, targetEl, msg, null, null, logMsgSrcId);
			} else {
				if (!idSet.add(idValue)) {
					LogReference srcRef = LogReference.getRef("CM", "cm001a");
					String msg = idAttribute + " is not unique";
					int msgLevel;
					if (reqUniqueness) {
						msgLevel = LogMgmt.LEV_ERR;
						curFileIsValid = false;
					} else {
						msgLevel = LogMgmt.LEV_WARN;
					}
					logIssue(LogMgmt.TAG_MD, msgLevel, targetEl, msg, null, srcRef, logMsgSrcId);
				}
				if (chkSyntax) {
					/*
					 * Validate identifier structure conforms with Sec 2.1 of
					 * Common Metadata spec (v2.4)
					 */

					String idSyntaxPattern = "[\\S-[:]]+:[\\S-[:]]+:[\\S-[:]]+:[\\S]+$";
					if (!idValue.matches(idSyntaxPattern)) {
						String msg = "ID syntax does not conform to recommendations.";
						String details = "Best Practice is use of 'md:<type>:<scheme>:<SSID> syntax";
						LogReference srcRef = LogReference.getRef("MMM-BP", "mmbp01.3");
						logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_WARN, targetEl, msg, details, srcRef, logMsgSrcId);
						// curFileIsValid = false;
					} else {
						String[] idParts = idValue.split(":");
						String idNid = idParts[0];
						String idType = idParts[1];
						String idScheme = idParts[2];
						String idSSID = idValue.split(":" + idScheme + ":")[1];
						validateIdScheme(idScheme, targetEl);
						validateIdSsid(idSSID, idScheme, targetEl);
						validateIdType(idType, idAttribute, targetEl);
					}
				}
			}
		}
		idSets.put(idElement, idSet);
		id2XmlMappings.put(idElement, id2XmlMap);
		idXRefCounts.put(idElement, idXRefCounter);
		return idSet;
	}

	/**
	 * @param idType
	 * @param idAttribute
	 * @param targetEl
	 */
	protected void validateIdType(String idType, String idAttribute, Element targetEl) {
		/*
		 * Check syntax of the 'type' as defined in Manifest/Avails Delivery
		 * Best Practices (BP-META-MMMD) Section 3.1.7
		 */
		String type = id2typeMap.get(idAttribute);
		if (type == null) {
			type = idAttribute.toLowerCase();
		}
		if (!idType.equals(type)) {
			LogReference srcRef = LogReference.getRef("MMM-BP", "mmbp01.3");
			String msg = "ID <type> does not conform to recommendation (i.e. '" + type + "')";
			logIssue(LogMgmt.TAG_BEST, LogMgmt.LEV_NOTICE, targetEl, msg, null, srcRef, logMsgSrcId);
		}

	}

	/**
	 * @param idSchema
	 * @param targetEl
	 */
	protected void validateIdScheme(String idScheme, Element targetEl) {
		if (!idScheme.startsWith("eidr")) {
			String msg = "Use of EIDR-based identifiers is recommended";
			String details = "Best Practice is to derive IDs from an EIDR-base ALID";
			LogReference srcRef = LogReference.getRef("MMM-BP", "mmbp01.1");
			logIssue(LogMgmt.TAG_BEST, LogMgmt.LEV_NOTICE, targetEl, msg, details, srcRef, logMsgSrcId);
		}
	}

	protected void validateIdSsid(String idSSID, String idScheme, Element targetEl) {
		String idPattern = null;
		LogReference srcRef = null;
		switch (idScheme) {
		case "eidr":
			srcRef = LogReference.getRef("MMM-BP", "mmbp01.2");
			String msg = "Use of EIDR-x or EIDR-s identifiers is recommended";
			logIssue(LogMgmt.TAG_BEST, LogMgmt.LEV_NOTICE, targetEl, msg, null, srcRef, logMsgSrcId);

			srcRef = null;
			idPattern = "10\\.[\\d]{4}/[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-Z]";
			break;
		case "eidr-s":
			srcRef = LogReference.getRef("EIDR-IDF", "eidr01-s");
			idPattern = "[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-Z]";
			break;
		case "eidr-x":
			srcRef = LogReference.getRef("EIDR-IDF", "eidr01-x");
			idPattern = "[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-Z]:[\\S]+";
			break;
		case "eidr-urn":
			srcRef = LogReference.getRef("EIDR-IDF", "eidr01-urn");
			idPattern = "urn:eidr:10\\.5240:[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-F]{4}-[\\dA-Z]";
			break;
		default:
			msg = "ID uses scheme '" + idScheme + "', SSID format will not be verified";
			logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_INFO, targetEl, msg, "ssid='" + idSSID + "'", null, logMsgSrcId);
			return;
		}
		boolean match = idSSID.matches(idPattern);
		if (!match) {
			String msg = "Invalid SSID syntax for " + idScheme + " scheme";
			logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_ERR, targetEl, msg, "ssid='" + idSSID + "'", srcRef, logMsgSrcId);
			curFileIsValid = false;
		}
	}

	/**
	 * @param srcElement
	 * @param xrefElement
	 * @param targetElType
	 * @deprecated
	 */
	protected void validateXRef(String srcElement, String xrefElement, String targetElType) {
		String xpath = ".//manifest:" + srcElement + "/manifest:" + xrefElement;
		validateXRef(xpath, targetElType);
	}

	/**
	 * @param srcElement
	 * @param xrefElement
	 * @param targetElType
	 */
	protected void validateXRef(String xpath, String targetElType) {
		HashSet<String> idSet = idSets.get(targetElType);
		Map<String, XrefCounter> idXRefCounter = idXRefCounts.get(targetElType);
		XPathExpression<Element> xpExpression = xpfac.compile(xpath, Filters.element(), null, manifestNSpace);
		List<Element> elementList = xpExpression.evaluate(curRootEl);
		for (int i = 0; i < elementList.size(); i++) {
			Element refEl = (Element) elementList.get(i);
			String targetId = refEl.getTextNormalize();
			if (!idSet.contains(targetId)) {
				String msg = "Invalid cross-reference: the referenced " + targetElType
						+ " is not defined in this manifest";
				logIssue(LogMgmt.TAG_MD, LogMgmt.LEV_ERR, refEl, msg, null, null, logMsgSrcId);
				curFileIsValid = false;
			} else {
				XrefCounter count = idXRefCounter.get(targetId);
				if (count == null) {
					System.out.println("TRAPPED :" + targetId);
					count = new XrefCounter(targetElType, targetId);
					idXRefCounter.put(targetId, count);
				}
				count.increment();
			}
		}
	}

	/**
	 * 
	 */
	protected void checkForOrphans() {
		// Map<String, Map<String, XrefCounter>> idXRefCounts;
		Iterator<Map<String, XrefCounter>> allCOunters = idXRefCounts.values().iterator();
		while (allCOunters.hasNext()) {
			// get the type-specific collection of counters...
			Map<String, XrefCounter> nextCSet = allCOunters.next();
			// now iterate thru the instance-specific counts..
			Iterator<XrefCounter> typeCounters = nextCSet.values().iterator();
			while (typeCounters.hasNext()) {
				XrefCounter nextCount = typeCounters.next();
				nextCount.validate();
			}
		}

	}

	/**
	 * returns true if string defines a non-negative integer value
	 * 
	 * @param indexValue
	 * @return
	 */
	public boolean isValidIndex(String indexValue) {
		try {
			int iv = Integer.parseInt(indexValue);
			return (iv >= 0);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	// ########################################################################

	/**
	 * Use the specified <tt>xpath</tt> to retrieve and validate image
	 * resolution. The resolution must be a String in the form colxrow (e.g.,
	 * 800x600 would mean an image 800 pixels wide and 600 pixels tall).
	 * 
	 * @param xpath
	 */
	protected void validateResolution(String xpath) {
		LogReference docRef = LogReference.getRef("CM", "cm_res");
		String msg = "Invalid image resolution";
		String details = "resolution must be in the form colxrow (e.g. 800x600)";
		String pattern = "\\d+x\\d+";
		XPathExpression<?> xpExpression = StructureValidation.resolveXPath(xpath);
		List<?> targetList = xpExpression.evaluate(curRootEl);
		for (Object target : targetList) {
			String text = null;
			Element targetEl = null;
			Attribute targetAt = null;
			if (target instanceof Element) {
				targetEl = (Element) target;
				text = targetEl.getTextNormalize();
			} else if (target instanceof Attribute) {
				targetAt = (Attribute) target;
				text = targetAt.getValue();
			}
			if (target != null) {
				boolean match = text.matches(pattern);
				if (!match) {
					if (targetEl == null) {
						targetEl = targetAt.getParent();
					}
					logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_ERR, targetEl, msg, details, docRef, logMsgSrcId);
				}
			}
		}
	}

	/**
	 * Locate and validate all instances of a &lt;md:Rating&gt; Element.
	 * Validation checks will be based on the data contained in the latest
	 * release of the MovieLabs Common Metadata Ratings.
	 * 
	 * @see <a href="http://www.movielabs.com/md/ratings/index.html">MovieLabs
	 *      Common Metadata Ratings</a>
	 * @return
	 */
	protected void validateRatings() {
		XPathExpression<Element> xpExp01 = xpfac.compile(".//md:Rating", Filters.element(), null, mdNSpace);
		List<Element> ratingElList = xpExp01.evaluate(curRootEl);
		rLoop: for (int i = 0; i < ratingElList.size(); i++) {
			Element ratingEl = ratingElList.get(i);
			Element rSysEl = ratingEl.getChild("System", mdNSpace);
			String system = rSysEl.getTextNormalize();
			if (system.isEmpty()) {
				String msg = "Rating System not specified";
				String explanation = null;
				logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_ERR, rSysEl, msg, explanation, null, logMsgSrcId);
				curFileIsValid = false;
				continue;
			}
			RatingSystem rSystem = RatingSystem.factory(system);
			if (rSystem == null) {
				String msg = "Unrecognized Rating System '" + system + "'";
				String explanation = null;
				logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_ERR, rSysEl, msg, explanation, null, logMsgSrcId);
				curFileIsValid = false;
				continue;
			}
			Element valueEl = ratingEl.getChild("Value", mdNSpace);
			String rating = valueEl.getTextNormalize();
			if (!specialRatings.contains(rating)) {
				if (!rSystem.isValid(rating)) {
					String msg = "Invalid Rating for RatingSystem";
					String explanation = "The " + system + " Rating System does not include the '" + rating + "'";
					logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_ERR, valueEl, msg, explanation, null, logMsgSrcId);
					curFileIsValid = false;
				} else if (rSystem.isDeprecated(rating)) {
					String msg = "Deprecated Rating";
					String explanation = "The " + system + " Rating System has deprecated the '" + rating
							+ "' rating. A more recent rating should be used if one is available";
					logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_WARN, valueEl, msg, explanation, null, logMsgSrcId);
				}
			}
			/*
			 * Is the RatingSystem in use in the specified country or
			 * countryRegion?
			 * 
			 */
			LogReference srcRef = LogReference.getRef("CM", "2.4", "cm_regions");
			Element regionEl = ratingEl.getChild("Region", mdNSpace);
			String region = null;
			Element target = regionEl.getChild("country", mdNSpace);
			if (target == null) {
				target = regionEl.getChild("countryRegion", mdNSpace);
				region = target.getText();
				/*
				 * We don't check validity of ISO-3166-2 codes as there are too
				 * many defined but very few used for ratings
				 */
				if (!rSystem.isUsedInSubRegion(region)) {
					String msg = "RatingSystem not used in specified Country SubRegion";
					String explanation = "The " + system
							+ " Rating System has not been officially adopted in SubRegion '" + region + "'";
					logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_WARN, target, msg, explanation, null, logMsgSrcId);
				}
			} else {
				region = target.getText();
				/* Is it valid ISO-3166-1 code? */
				if (!iso3166_1_codes.containsKey(region)) {
					String msg = "Invalid code for country";
					String explanation = "A country should be specified as a ISO 3166-1 Alpha-2 code";
					logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_ERR, target, msg, explanation, srcRef, logMsgSrcId);
					curFileIsValid = false;
					;
				} else if (!rSystem.isUsedInRegion(region)) {
					String msg = "RatingSystem not used in specified Region";
					String explanation = "The " + system + " Rating System has not been officially adopted in Region '"
							+ region + "'";
					logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_WARN, target, msg, explanation, null, logMsgSrcId);
				}
			}
			/*
			 * Validation of Reasons: IF RatingSys provides defined reason codes
			 * then validate zero or more codes ELSE allow any string value.
			 * 
			 */
			if (rSystem.providesReasons()) {
				List<Element> reasonList = ratingEl.getChildren("Reason", mdNSpace);
				for (int j = 0; j < reasonList.size(); j++) {
					Element reasonEl = reasonList.get(j);
					String reason = reasonEl.getTextNormalize();
					if (!rSystem.hasReason(reason)) {
						String msg = "Invalid Reason code";
						String explanation = "Rating System uses pre-defined Reason-Codes which do not include '"
								+ reason + "'";
						logIssue(LogMgmt.TAG_CR, LogMgmt.LEV_ERR, reasonEl, msg, explanation, null, logMsgSrcId);
						curFileIsValid = false;
					}
				}
			}
		}
	}

	/**
	 * Validate a language entry conforms to RFC5646. This method is a more
	 * flexible way to check language codes than
	 * <tt>validateLanguage(Namespace primaryNS)</tt> in that the arguments
	 * allow full specification of the source of the language code.
	 * <p>
	 * The source of the entry is the <i>child</i> node. The child may be:
	 * <ul>
	 * <li>A JDOM Element,</li>
	 * <li>an attribute of the primary JDOM Element (as indicated by the '@'
	 * prefix), or</li>
	 * <li><tt>null</tt>, in which case the text value of the <tt>primaryEl</tt>
	 * will be used.</li>
	 * </ul>
	 * </p>
	 * <p>
	 * Use of RFC5646 is limited to a subset of the complete syntax in that only
	 * the <tt>Language</tt>, <tt>Region</tt>, and <tt>Variant</tt> subtags are
	 * supported. Use of the <tt>Script</tt> subtag is not supported.
	 * </p>
	 * 
	 * @param primaryNS
	 * @param primaryEl
	 * @param childNS
	 * @param child
	 * @return
	 * @see validateLanguage(Namespace primaryNS)
	 */
	protected void validateLanguage(Namespace primaryNS, String primaryEl, Namespace childNS, String child) {
		XPathExpression<Element> xpExpression = xpfac.compile(".//" + primaryNS.getPrefix() + ":" + primaryEl,
				Filters.element(), null, primaryNS);
		List<Element> elementList = xpExpression.evaluate(curRootEl);
		int tag4log = getLogTag(primaryNS, childNS);
		for (int i = 0; i < elementList.size(); i++) {
			Element targetEl = (Element) elementList.get(i);
			String text;
			if (child == null) {
				text = targetEl.getTextNormalize();
				if (!checkLangTag(text)) {
					reportLangError(targetEl, tag4log, text);
				}
			} else if (child.startsWith("@")) {
				// dealing with an attribute
				String targetAttb = child.replaceFirst("@", "");
				text = targetEl.getAttributeValue(targetAttb);
				if (!checkLangTag(text)) {
					reportLangError(targetEl, tag4log, text);
				}
			} else {
				// dealing with one or more child elements
				List<Element> langElList = targetEl.getChildren(child, childNS);
				for (int j = 0; j < langElList.size(); j++) {
					Element langEl = (Element) langElList.get(j);
					text = langEl.getTextNormalize();
					if (!checkLangTag(text)) {
						reportLangError(langEl, tag4log, text);
					}
				}
			}
		}
	}

	/**
	 * Validate language codes specified in any <tt>@language</tt> attribute
	 * conform to RFC5646.
	 * 
	 * @param primaryNS
	 *            used only for log messages
	 */
	protected void validateLanguage(Namespace primaryNS) {
		XPathExpression<Attribute> xpExpression = xpfac.compile("//@language", Filters.attribute(), null, primaryNS);
		List<Attribute> attList = xpExpression.evaluate(curRootEl);
		int tag4log = getLogTag(primaryNS, null);
		for (int i = 0; i < attList.size(); i++) {
			Attribute targetAtt = (Attribute) attList.get(i);
			String text = targetAtt.getValue();
			if (!checkLangTag(text)) {
				reportLangError(targetAtt.getParent(), tag4log, text);
			}
		}
	}

	private boolean checkLangTag(String text) {
		if (text == null) {
			/*
			 * something was missing. If it was REQ the XSD-based (i.e., schema)
			 * validation will flag it.
			 */
			return true;
		}
		if (text.isEmpty()) {
			/*
			 * The habit some folks have is to enter a required element (which
			 * passes schema check) but leave the value empty. THAT IS AN ERROR!
			 */
			return false;
		}
		/*
		 * RFC4647 states matching of language codes is case-insensitive. The
		 * codes have been converted and stored as all lowercase so we do the
		 * same conversion of the value we are checking.
		 * 
		 */
		text = text.toLowerCase();
		String[] langSubfields = text.split("-");
		boolean passed = true;
		/*
		 * 1st field should be specified in ISO639-2 or ISO639-3 and will be
		 * MANDATORY
		 */
		String subTag = langSubfields[0];
		switch (subTag.length()) {
		case 2:
			passed = iso639_2.contains(subTag);
			break;
		case 3:
			passed = iso639_3.contains(subTag);
			break;
		default:
			passed = false;
		}
		if (!passed) {
			return false;
		}
		if (langSubfields.length < 2) {
			return true;
		}

		/*
		 * 2nd field will be script or region or a variant. Which it is can be
		 * determined by the length of string.
		 */
		subTag = langSubfields[1];
		boolean foundRegion = false;
		switch (subTag.length()) {
		case 2:
		case 3:
			passed = iso3166_1_codes.containsKey(subTag.toUpperCase());
			foundRegion = true;
			break;
		case 4:
			passed = rfc5646Script.contains(subTag);
			break;
		default:
			passed = rfc5646Variant.contains(subTag);
		}
		if (!passed) {
			return false;
		}
		if (langSubfields.length < 3) {
			return true;
		}

		/*
		 * 3rd field may be region or a variant. Make sure we didn't already
		 * process a region in subtag #2.
		 */
		subTag = langSubfields[2];
		boolean foundVariant = false;
		if ((subTag.length() == 2) && !foundRegion) {
			passed = iso3166_1_codes.containsKey(subTag.toUpperCase());
		} else {
			passed = rfc5646Variant.contains(subTag);
			foundVariant = true;
		}
		if (!passed) {
			return false;
		}
		if (langSubfields.length < 4) {
			return true;
		}

		/*
		 * 4th field can only be a variant. Make sure we didn't already process
		 * a variant in prior subtag.
		 */
		if (foundVariant) {
			passed = false;
		} else {
			passed = rfc5646Variant.contains(langSubfields[3]);
		}
		return passed;
	}

	private void reportLangError(Element targetEl, int tag4log, String langTag) {
		/*
		 * Build an appropriate log entry based on nature and structure of the
		 * value source
		 */
		String errMsg = "Invalid Language code value '" + langTag + "'";
		String details = "Language encoding must conform to RFC5646 syntax and use registered subtag value";
		LogReference srcRef = LogReference.getRef("CM", CM_VER, "cm_lang");
		logIssue(tag4log, LogMgmt.LEV_ERR, targetEl, errMsg, details, srcRef, logMsgSrcId);
		curFileIsValid = false;
	}

	protected boolean validateRegion(Namespace primaryNS, String primaryEl, Namespace childNS, String child) { 
		LogReference srcRef = LogReference.getRef("CM", "cm_regions");
		return validateCode(primaryNS, primaryEl, childNS, child, iso3166_1_codes, srcRef, false);
	}
	
	/**
	 * Validate country codes specified in any <tt>@region</tt> attribute
	 * conform to ISO 3166-1
	 * 
	 * @param primaryNS
	 *            used only for log messages
	 */
	protected boolean validateRegion(Namespace primaryNS) {
		boolean allOK = true;
		String errMsg = "Unrecognized value for @region attribute";
		LogReference srcRef = LogReference.getRef("CM",   "cm_regions");
		XPathExpression<Attribute> xpExpression = xpfac.compile("//@region", Filters.attribute(), null, primaryNS);
		List<Attribute> attList = xpExpression.evaluate(curRootEl);
		int tag4log = getLogTag(primaryNS, null);
		for (int i = 0; i < attList.size(); i++) {
			Attribute targetAtt = (Attribute) attList.get(i);
			String text = targetAtt.getValue();
			if (!iso3166_1_codes.containsKey(text)) {
				logIssue(tag4log, LogMgmt.LEV_ERR, targetAtt.getParent(), errMsg, null, srcRef, logMsgSrcId);
				allOK = false;
				curFileIsValid = false;
			}
		}
		return allOK;
	}

	protected boolean validateCode(Namespace primaryNS, String primaryEl, Namespace childNS, String child,
			Properties codes, LogReference srcRef, boolean caseSensitive) {
		boolean allOK = true;
		int tag4log = getLogTag(primaryNS, childNS);
		XPathExpression<Element> xpExpression = xpfac.compile(".//" + primaryNS.getPrefix() + ":" + primaryEl,
				Filters.element(), null, primaryNS);
		List<Element> elementList = xpExpression.evaluate(curRootEl);
		for (int i = 0; i < elementList.size(); i++) {
			String text = null;
			String errMsg = null;
			Element logMsgEl = null;
			Element targetEl = (Element) elementList.get(i);
			if (!child.startsWith("@")) {
				Element subElement = targetEl.getChild(child, childNS);
				logMsgEl = subElement;
				if (subElement != null) {
					text = subElement.getTextNormalize();
					errMsg = "Unrecognized value '" + text + "' for " + primaryEl + "/" + child;
				}
			} else {
				String targetAttb = child.replaceFirst("@", "");
				text = targetEl.getAttributeValue(targetAttb);
				logMsgEl = targetEl;
				errMsg = "Unrecognized value '" + text + "' for attribute " + child;
			}
			if (text != null) {
				if (!caseSensitive) {
					text = text.toUpperCase();
				}
				if (!codes.containsKey(text)) {
					logIssue(tag4log, LogMgmt.LEV_ERR, logMsgEl, errMsg, null, srcRef, logMsgSrcId);
					allOK = false;
					curFileIsValid = false;
				}
			}
		}
		return allOK;
	}

	protected void validateVocab(Namespace primaryNS, String primaryEl, Namespace childNS, String child,
			JSONArray expected, LogReference srcRef, boolean caseSensitive) {
		validateVocab(primaryNS, primaryEl, childNS, child, expected, srcRef, caseSensitive, true);
	}

	/**
	 * @param primaryNS
	 * @param primaryEl
	 * @param childNS
	 * @param child
	 * @param expected
	 * @param srcRef
	 * @param caseSensitive
	 * @return
	 */
	protected void validateVocab(Namespace primaryNS, String primaryEl, Namespace childNS, String child,
			JSONArray expected, LogReference srcRef, boolean caseSensitive, boolean strict) {
		if (expected == null || expected.isEmpty()) {
			/*
			 * The version of the schema being used does not define an
			 * enumerated set of valid terms for the target element.
			 * 
			 */
			return;
		}
		XPathExpression<Element> xpExpression = xpfac.compile(".//" + primaryNS.getPrefix() + ":" + primaryEl,
				Filters.element(), null, primaryNS);
		List<Element> elementList = xpExpression.evaluate(curRootEl);
		int tag4log = getLogTag(primaryNS, childNS);
		String optionsString = expected.toString().toLowerCase();
		for (int i = 0; i < elementList.size(); i++) {
			String text = null;
			String errMsg = null;
			Element logMsgEl = null;
			Element targetEl = (Element) elementList.get(i);
			if (!child.startsWith("@")) {
				Element subElement = targetEl.getChild(child, childNS);
				logMsgEl = subElement;
				if (subElement != null) {
					text = subElement.getTextNormalize();
					errMsg = "Unrecognized value '" + text + "' for " + primaryEl + "/" + child;
				}
			} else {
				String targetAttb = child.replaceFirst("@", "");
				text = targetEl.getAttributeValue(targetAttb);
				logMsgEl = targetEl;
				errMsg = "Unrecognized value '" + text + "' for attribute " + child;
			}
			if (text != null) {
				int logLevel;
				String explanation;
				if (strict) {
					logLevel = LogMgmt.LEV_ERR;
					explanation = "Value specified does not match one of the allowed strings.";
					if (caseSensitive) {
						explanation = explanation + " Note that string-matching is case-sensitive";
					}
				} else {
					logLevel = LogMgmt.LEV_WARN;
					explanation = "Value specified doesn't match one of the recommended strings. Others may be used if all parties agree";
				}
				boolean matched = true;
				if (caseSensitive) {
					if (!expected.contains(text)) {
						logIssue(tag4log, logLevel, logMsgEl, errMsg, explanation, srcRef, logMsgSrcId);
						matched = false;

					}
				} else {
					String checkString = "\"" + text.toLowerCase() + "\"";
					if (!optionsString.contains(checkString)) {
						logIssue(tag4log, logLevel, logMsgEl, errMsg, explanation, srcRef, logMsgSrcId);
						matched = false;
					}
				}
				if (!matched && strict) {
					curFileIsValid = false;
				}
			}
		}
	}

	/**
	 * @param primaryNS
	 * @param childNS
	 * @return
	 */
	private int getLogTag(Namespace primaryNS, Namespace childNS) {
		int tag4log = LogMgmt.TAG_N_A;
		Namespace tagNS;
		if (childNS != null) {
			tagNS = childNS;
		} else {
			tagNS = primaryNS;
		}
		if (tagNS == manifestNSpace) {
			tag4log = LogMgmt.TAG_MANIFEST;
		} else if (tagNS == availsNSpace) {
			tag4log = LogMgmt.TAG_AVAIL;
		} else if (tagNS == mdNSpace) {
			tag4log = LogMgmt.TAG_MD;
		}
		return tag4log;
	}
}
