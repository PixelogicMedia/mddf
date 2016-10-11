/**
 * Copyright (c) 2016 MovieLabs

 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.movielabs.mddflib.avails.xml;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.jdom2.Element;
import org.jdom2.Namespace;

import com.movielabs.mddflib.avails.xlsx.AvailsSheet;

/**
 * Code and functionality formerly in SheetRow. The XML generated reflects a
 * "best effort" in that there is no guarantee that it is valid.
 * <p>
 * This class is intended to have a low footprint in terms of memory usage so as
 * to facilitate processing of sheets with large row counts. Note that Excel
 * 2010 supports up to 1,048,576 rows.
 * </p>
 * 
 * @author L. Levin, Critical Architectures LLC
 *
 */
public class RowToXmlHelper {

	private static final String MISSING = "--FUBAR (missing)";
	protected Row row;
	protected String shortDesc = ""; // default
	protected XmlBuilder xb;
	private AvailsSheet sheet;
	private String workType = "";
	private DataFormatter dataF = new DataFormatter();

	/**
	 * @param fields
	 */
	RowToXmlHelper(AvailsSheet sheet, Row row, String desc) {
		super();
		this.sheet = sheet;
		this.row = row;
		if (desc != null) {
			this.shortDesc = desc;
		}

	}

	protected Element makeAvail(XmlBuilder xb) throws Exception {
		this.xb = xb;
		Element avail = new Element("Avail", xb.getAvailsNSpace());
		Element e;

		// ALID
		process(avail, "ALID", xb.getAvailsNSpace(), "Avail/AvailID");

		// Disposition
		String value = getColumnData("Disposition/EntryType");
		if (xb.isRequired("Disposition", "avails") || isSpecified(value)) {
			avail.addContent(mDisposition(value));
		}

		// Licensor
		value = getColumnData("Avail/DisplayName");
		if (xb.isRequired("Licensor", "avails") || isSpecified(value)) {
			avail.addContent(mPublisher("Licensor", value));
		}

		// Service Provider (OPTIONAL)
		value = getColumnData("Avail/ServiceProvider");
		if (xb.isRequired("ServiceProvider", "avails") || isSpecified(value)) {
			avail.addContent(mPublisher("ServiceProvider", value));
		}

		/*
		 * Need to save the current workType for use in Transaction/Terms
		 */
		this.workType = getColumnData("AvailAsset/WorkType");
		String availType;
		switch (workType) {
		case "Movie":
		case "Short":
			availType = "single";
			break;
		case "Season":
			availType = "season";
			break;
		case "Episode":
			availType = "episode";
			break;
		default:
			availType = "";
		}

		// AvailType (e.g., 'single' for a Movie)
		avail.addContent(mGenericElement("AvailType", availType, xb.getAvailsNSpace()));

		// ShortDescription
		if (xb.isRequired("ShortDescription", "avails") || isSpecified(shortDesc)) {
			e = mGenericElement("ShortDescription", shortDesc, xb.getAvailsNSpace());
			avail.addContent(e);
		}
		// Asset
		if (xb.isRequired("WorkType", "avails") || isSpecified(workType)) {
			e = mAssetHeader(workType);
			avail.addContent(e);
		}

		// Transaction
		if ((e = mTransactionHeader()) != null)
			avail.addContent(e);

		// Exception Flag
		process(avail, "ExceptionFlag", xb.getAvailsNSpace(), "Avail/ExceptionFlag");

		return avail;
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	protected Element mDisposition(String entryType) {
		Element disp = new Element("Disposition", xb.getAvailsNSpace());
		Element entry = new Element("EntryType", xb.getAvailsNSpace());
		entry.setText(entryType);
		disp.addContent(entry);
		return disp;
	}

	/**
	 * Create an <tt>mdmec:Publisher-type</tt> XML element with a md:DisplayName
	 * element child, and populate the latter with the DisplayName
	 * 
	 * @param name
	 *            the parent element to be created (i.e., Licensor or
	 *            ServiceProvider)
	 * @param displayName
	 *            the name to be held in the DisplayName child node
	 * @return the created element
	 */
	protected Element mPublisher(String name, String displayName) {
		Element pubEl = new Element(name, xb.getAvailsNSpace());
		Element e = new Element("DisplayName", xb.getMdNSpace());
		e.setText(displayName);
		pubEl.addContent(e);
		// XXX ContactInfo mandatory but can't get this info from the
		// spreadsheet
		if (xb.isRequired("ContactInfo", "mdmec")) {
			e = new Element("ContactInfo", xb.getMdMecNSpace());
			Element e2 = new Element("Name", xb.getMdNSpace());
			e.addContent(e2);
			e2 = new Element("PrimaryEmail", xb.getMdNSpace());
			e.addContent(e2);
			pubEl.addContent(e);
		}
		return pubEl;
	}

	protected Element mAssetHeader(String workType) {
		Element asset = new Element("Asset", xb.getAvailsNSpace());
		Element wt = new Element("WorkType", xb.getAvailsNSpace());
		wt.setText(workType);
		asset.addContent(wt);
		return mAssetBody(asset);
	}

	/**
	 * Construct Element instantiating the <tt>AvailMetadata-type</tt>. This
	 * method should be extended or overridden by sub-classes specific to a
	 * given work type (i.e., Movie, Episode, Season, etc.)
	 * 
	 * @param asset
	 * @return
	 */
	protected Element mAssetBody(Element asset) {
		Element e;
		String contentID = getColumnData("AvailAsset/ContentID");
		if (isSpecified(contentID)) {
			asset.setAttribute("contentID", "contentID");
		}

		Element metadata = new Element("Metadata", xb.getAvailsNSpace());

		/*
		 * TitleDisplayUnlimited is OPTIONAL in SS but REQUIRED in XML;
		 * workaround by assigning it internal alias value
		 */
		String titleDU = getColumnData("AvailMetadata/TitleDisplayUnlimited");
		String titleAlias = getColumnData("AvailMetadata/TitleInternalAlias");
		if (!isSpecified(titleDU)) {
			titleDU = titleAlias;
		}
		// if (isSpecified(titleDU)) {
		e = mGenericElement("TitleDisplayUnlimited", titleDU, xb.getAvailsNSpace());
		metadata.addContent(e);
		// }

		// TitleInternalAlias
		if (xb.isRequired("TitleInternalAlias", "avails") || isSpecified(titleAlias)) {
			metadata.addContent(mGenericElement("TitleInternalAlias", titleAlias, xb.getAvailsNSpace()));
		}

		// ProductID --> EditEIDR-URN ( optional field)
		process(metadata, "EditEIDR-URN", xb.getAvailsNSpace(), "AvailAsset/ProductID");

		// ContentID --> TitleEIDR-URN ( optional field)
		process(metadata, "TitleEIDR-URN", xb.getAvailsNSpace(), "AvailAsset/ContentID");

		// AltID --> AltIdentifier
		String value = getColumnData("AvailMetadata/AltID");
		if (xb.isRequired("AltIdentifier", "avails") || isSpecified(value)) {
			Element altIdEl = new Element("AltIdentifier", xb.getAvailsNSpace());
			Element cid = new Element("Namespace", xb.getMdNSpace());
			cid.setText(MISSING);
			altIdEl.addContent(cid);
			altIdEl.addContent(mGenericElement("Identifier", value, xb.getMdNSpace()));
			Element loc = new Element("Location", xb.getMdNSpace());
			loc.setText(MISSING);
			altIdEl.addContent(loc);
			metadata.addContent(altIdEl);
		}

		process(metadata, "ReleaseDate", xb.getAvailsNSpace(), "AvailMetadata/ReleaseYear");
		process(metadata, "RunLength", xb.getAvailsNSpace(), "AvailMetadata/TotalRunTime");

		mReleaseHistory(metadata, "original", "AvailMetadata/ReleaseHistoryOriginal");
		mReleaseHistory(metadata, "DVD", "AvailMetadata/ReleaseHistoryPhysicalHV");

		process(metadata, "USACaptionsExemptionReason", xb.getAvailsNSpace(), "AvailMetadata/CaptionExemption");

		mRatings(metadata);

		process(metadata, "EncodeID", xb.getAvailsNSpace(), "AvailAsset/EncodeID");
		process(metadata, "LocalizationOffering", xb.getAvailsNSpace(), "AvailMetadata/LocalizationType");

		// Attach generated Metadata node
		asset.addContent(metadata);
		return asset;
	}

	protected Element mTransactionHeader() throws Exception {
		Element transaction = new Element("Transaction", xb.getAvailsNSpace());
		return mTransactionBody(transaction);
	}

	/**
	 * populate a Transaction element; called from superclass
	 * 
	 * @param transaction
	 *            parent node
	 * @return transaction parent node
	 */
	protected Element mTransactionBody(Element transaction) throws Exception {
		Element e;
		String prefix = "AvailTrans/";
		process(transaction, "LicenseType", xb.getAvailsNSpace(), prefix + "LicenseType");
		process(transaction, "Description", xb.getAvailsNSpace(), prefix + "Description");
		processRegion(transaction, "Territory", xb.getAvailsNSpace(), prefix + "Territory");

		// Start or StartCondition
		processCondition(transaction, "Start", xb.getAvailsNSpace(), prefix + "Start");
		// End or EndCondition
		processCondition(transaction, "End", xb.getAvailsNSpace(), prefix + "End");

		process(transaction, "StoreLanguage", xb.getAvailsNSpace(), prefix + "StoreLanguage");
		process(transaction, "LicenseRightsDescription", xb.getAvailsNSpace(), prefix + "LicenseRightsDescription");
		process(transaction, "FormatProfile", xb.getAvailsNSpace(), prefix + "FormatProfile");
		process(transaction, "ContractID", xb.getAvailsNSpace(), prefix + "ContractID");

		processTerm(transaction);

		// OtherInstructions
		// if ((e = mGenericElement(COL.OtherInstructions.toString(),
		// fields[COL.OtherInstructions.ordinal()],
		// false)) != null)
		// transaction.addContent(e);

		return transaction;
	}

	/**
	 * Add 1 or more Term elements to a Transaction.
	 * <p>
	 * As of Excel v 1.6. Terms are a mess. There are two modes for defining:
	 * <ol>
	 * <li>Use 'PriceType' cell to define a <tt>termName</tt> and then get value
	 * from 'PriceValue' cell, or</li>
	 * <li>the use of columns implicitly linked to specific <tt>termName</tt>
	 * </li>
	 * </ol>
	 * An example of the 2nd approach is the 'WatchDuration' column.
	 * </p>
	 * 
	 * @param transaction
	 */
	private void processTerm(Element transaction) {
		String prefix = "AvailTrans/";
		/*
		 * May be multiple 'terms'. Start with one specified via the PriceType
		 */
		String tName = getColumnData(prefix + "PriceType");
		if (isSpecified(tName)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			switch (tName) {
			case "Tier":
			case "Category":
				process(termEl, "Text", xb.getAvailsNSpace(), prefix + "PriceValue");
				break;
			case "WSP":
				if (workType.equals("Episode")) {
					tName = "EpisodeWSP";
				} else if (workType.equals("Season")) {
					tName = "SeasonWSP";
				}
			case "DMRP":
			case "SMRP":
				Element moneyEl = process(termEl, "Money", xb.getAvailsNSpace(), prefix + "PriceValue");
				String currency = getColumnData(prefix + "PriceCurrency");
				if (moneyEl != null && isSpecified(currency)) {
					moneyEl.setAttribute("currency", currency);
				}
				break;
			case "Season Only":
			}
			termEl.setAttribute("termName", tName);
		}
		/*
		 * Now look for Terms specified via other columns....
		 */

		// SRP Term
		String value = getColumnData(prefix + "SRP");
		if (isSpecified(value)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			termEl.setAttribute("termName", "SRP");
			Element childEl = new Element("Money", xb.getAvailsNSpace());
			termEl.addContent(childEl);
			termEl.setAttribute("currency", value);
		}

		value = getColumnData(prefix + "SuppressionLiftDate");
		if (isSpecified(value)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			termEl.setAttribute("termName", "AnnounceDate");
			Element childEl = mGenericElement("Event", value, xb.getAvailsNSpace());
			termEl.addContent(childEl);
		}

		value = getColumnData(prefix + "RentalDuration");
		if (isSpecified(value)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			termEl.setAttribute("termName", "RentalDuration");
			Element childEl = mGenericElement("Duration", value, xb.getAvailsNSpace());
			termEl.addContent(childEl);
		}

		value = getColumnData(prefix + "WatchDuration");
		if (isSpecified(value)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			termEl.setAttribute("termName", "WatchDuration");
			Element childEl = mGenericElement("Duration", value, xb.getAvailsNSpace());
			termEl.addContent(childEl);
		}

		value = getColumnData(prefix + "FixedEndDate");
		if (isSpecified(value)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			termEl.setAttribute("termName", "FixedEndDate");
			Element childEl = mGenericElement("Event", value, xb.getAvailsNSpace());
			termEl.addContent(childEl);
		}

		value = getColumnData(prefix + "HoldbackLanguage");
		if (isSpecified(value)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			termEl.setAttribute("termName", "HoldbackLanguage");
			Element childEl = mGenericElement("Language", value, xb.getAvailsNSpace());
			termEl.addContent(childEl);
		}

		value = getColumnData(prefix + "AllowedLanguages");
		if (isSpecified(value)) {
			Element termEl = new Element("Term", xb.getAvailsNSpace());
			transaction.addContent(termEl);
			termEl.setAttribute("termName", "HoldbackExclusionLanguage");
			Element childEl = mGenericElement("Language", value, xb.getAvailsNSpace());
			termEl.addContent(childEl);
		}
	}

	/**
	 * Create an Avails ExceptionFlag element
	 */
	protected Element mExceptionFlag(String exceptionFlag) {
		Element eFlag = new Element("ExceptionFlag", xb.getAvailsNSpace());
		eFlag.setText(exceptionFlag);
		return eFlag;
	}

	/**
	 * @param parentEl
	 * @param type
	 * @param cellKey
	 * @param row
	 */
	private void mReleaseHistory(Element parentEl, String type, String cellKey) {
		String value = getColumnData(cellKey);
		if (!isSpecified(value)) {
			return;
		}
		Element rh = new Element("ReleaseHistory", xb.getAvailsNSpace());
		Element rt = new Element("ReleaseType", xb.getMdNSpace());
		rt.setText(type);
		rh.addContent(rt);
		rh.addContent(mGenericElement("Date", value, xb.getMdNSpace()));
		parentEl.addContent(rh);
	}

	protected void mRatings(Element m) {

		String ratingSystem = getColumnData("AvailMetadata/RatingSystem");
		String ratingValue = getColumnData("AvailMetadata/RatingValue");
		String ratingReason = getColumnData("AvailMetadata/RatingReason");
		/*
		 * According to XML schema, all 3 values are REQUIRED for a Rating. If
		 * any has been specified than we add the Rating element and let XML
		 * validation worry about completeness,
		 */
		boolean add = isSpecified(ratingSystem) || isSpecified(ratingValue) || isSpecified(ratingReason);
		if (!add) {
			return;
		}
		Element ratings = new Element("Ratings", xb.getAvailsNSpace());
		Element rat = new Element("Rating", xb.getMdNSpace());
		ratings.addContent(rat);
		Element region = new Element("Region", xb.getMdNSpace());
		String territory = getColumnData("AvailTrans/Territory");
		if (isSpecified(territory)) {
			Element country = new Element("country", xb.getMdNSpace());
			region.addContent(country);
			country.setText(territory);
		}
		rat.addContent(region);

		if (isSpecified(ratingSystem)) {
			rat.addContent(mGenericElement("System", ratingSystem, xb.getMdNSpace()));
		}

		if (isSpecified(ratingValue)) {
			rat.addContent(mGenericElement("Value", ratingValue, xb.getMdNSpace()));
		}
		if (isSpecified(ratingReason)) {
			String[] reasons = ratingReason.split(",");
			for (String s : reasons) {
				Element reason = mGenericElement("Reason", s, xb.getMdNSpace());
				rat.addContent(reason);
			}
		}
		m.addContent(ratings);
	}

	/**
	 * Create an XML element
	 * 
	 * @param name
	 *            the name of the element
	 * @param val
	 *            the value of the element
	 * @return the created element, or null
	 */
	protected Element mGenericElement(String name, String val, Namespace ns) {
		Element el = new Element(name, ns);
		String formatted = xb.formatForType(name, ns, val);
		el.setText(formatted);
		return el;
	}

	protected Element process(Element parentEl, String childName, Namespace ns, String cellKey) {
		Pedigree pg = getData(cellKey);
		if (pg == null) {
			System.out.println("Row2Xml.process:: Row " + row.getRowNum() + "  [" + cellKey + "]--->NULL<--");
			return null;
		}
		String value = pg.getRawValue();
		if (xb.isRequired(childName, ns.getPrefix()) || isSpecified(value)) {
			Element childEl = mGenericElement(childName, value, ns);
			parentEl.addContent(childEl);
			xb.addToPedigree(childEl, pg);
			// System.out.println("Row2Xml.process:: Row "+row.getRowNum() + " [" + cellKey +
			// "]--->" + value + "<--");
			return childEl;
		} else {
			return null;
		}
	}

	private void processRegion(Element parentEl, String regionType, Namespace ns, String cellKey) {
		Element regionEl = new Element(regionType, ns);
		Element countryEl = process(regionEl, "country", xb.getMdNSpace(), cellKey);
		if (countryEl != null) {
			parentEl.addContent(regionEl);
		}
	}

	/**
	 * Process start or end conditions for a Transaction.
	 * 
	 * @param parentEl
	 * @param childName
	 * @param ns
	 * @param cellKey
	 * @param row
	 * @return
	 */
	private boolean processCondition(Element parentEl, String childName, Namespace ns, String cellKey) {
		Pedigree pg = getData(cellKey);
		String value = pg.getRawValue();
		if (isSpecified(value)) {
			Element condEl = null;
			// does it start with 'yyyy' ?
			if (value.matches("^\\d[.]*")) {
				condEl = mGenericElement(childName, value, ns);
			} else {
				condEl = mGenericElement(childName + "Condition", value, ns);
			}
			parentEl.addContent(condEl);
			xb.addToPedigree(condEl, pg);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * @param colKey
	 * @return
	 * @deprecated Use getData(String colKey)
	 */
	private String getColumnData(String colKey) {
		int cellIdx = sheet.getColumnIdx(colKey);
		if (cellIdx < 0) {
			return null;
		} else {
			Cell cell = row.getCell(cellIdx);
			String value = dataF.formatCellValue(cell);
			if (value == null) {
				value = "";
			}
			return value;
		}
	}

	private Pedigree getData(String colKey) {
		int cellIdx = sheet.getColumnIdx(colKey);
		if (cellIdx < 0) {
			return null;
		}
		Cell sourceCell = row.getCell(cellIdx);
		String value = dataF.formatCellValue(sourceCell);
		if (value == null) {
			value = "";
		}
		Pedigree ped = new Pedigree(sourceCell, value);

		return ped;
	}

	/**
	 * Returns <tt>true</tt> if the value is both non-null and not empty.
	 * 
	 * @param value
	 * @return
	 */
	protected boolean isSpecified(String value) {
		return (value != null && (!value.isEmpty()));
	}

}