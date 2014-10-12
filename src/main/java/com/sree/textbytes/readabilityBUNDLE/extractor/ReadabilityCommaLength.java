package com.sree.textbytes.readabilityBUNDLE.extractor;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import com.sree.textbytes.StringHelpers.StopWords;
import com.sree.textbytes.StringHelpers.WordStats;
import com.sree.textbytes.StringHelpers.string;
import com.sree.textbytes.readabilityBUNDLE.AddSiblings;
import com.sree.textbytes.readabilityBUNDLE.Patterns;
import com.sree.textbytes.readabilityBUNDLE.ScoreInfo;

public class ReadabilityCommaLength extends ReadabilitySnack {
	@Override
	public Element fetchArticleContent(Document document) {
		Element topNode = null;
		Set<Element> parentNodes = new HashSet<Element>();
		Collection<Element> nodesToCheck = getNodesToScore(document);

		for (Element element : nodesToCheck) {
			Element parentElement = element.parent();
			if (parentElement == null) {
				logger.debug("Cannot find parent node, ignoring "
						+ element.tagName());
				continue;
			}

			if (element.text().length() < 25) {
				logger.debug("Inner Text less than critical , ignoring "
						+ element.tagName());
				continue;
			}
			double contentScore = getElementScore(element);
			logger.debug("Content Score : " + contentScore + "Element "
					+ element.tagName());
			ScoreInfo.updateContentScore(element.parent(), contentScore);
			ScoreInfo.updateContentScore(element.parent().parent(),
					contentScore / 2);
			if (!parentNodes.contains(element.parent())) {
				parentNodes.add(element.parent());

			}
			if (!parentNodes.contains(element.parent().parent())) {
				parentNodes.add(element.parent().parent());

			}
		}

		double topNodeScore = 0;
		for (Element e : parentNodes) {
			double score = ScoreInfo.getContentScore(e);
			if (score > topNodeScore) {
				topNode = e;
				topNodeScore = score;

			}
			if (topNode == null) {
				topNode = e;

			}

		}

		if (topNode != null) {
			topNode = addSiblings(topNode, topNodeScore);
		}

		return topNode;
	}

	@Override
	public double getElementScore(Element element) {
		double contentScore = 0;
		++contentScore; // add a point for paragraph itself as a base

		String innerText = element.text();
		int commaCount = innerText.split(",").length;
		contentScore += commaCount; // add # number comma

		int hundredChar = (int) Math.floor(innerText.length() / 100);
		int charCountScore = Math.min(hundredChar, 3);
		contentScore += charCountScore;

		return contentScore;
	}

	protected Collection<Element> getNodesToScore(Document doc) {
		Map<Element, Object> nodesToCheck = new LinkedHashMap<Element, Object>(
				64);
		for (Element element : doc.select("body").select("*")) {
			if ("p;td;h1;h2;pre".contains(element.tagName())) {
				nodesToCheck.put(element, null);
			}
		}
		return nodesToCheck.keySet();
	}

	protected boolean qualifyToAppendSibling(Element node,
			Element currentSibling, double siblingScoreThreshold) {
		boolean append = false;
		double contentBonus = 0;

		double siblingScore = getElementScore(currentSibling) + contentBonus;

		if (siblingScore >= siblingScoreThreshold) {
			append = true;
		}

		if (currentSibling.tagName().toUpperCase().equals("P")) {
			double linkDensity = getLinkDensity(currentSibling);
			String nodeText = currentSibling.text();
			int nodeLength = nodeText.length();

			if (nodeLength > 80 && linkDensity < 0.25) {
				append = true;
			} else if (nodeLength < 80 && linkDensity == 0
					&& nodeText.matches("\\.( |$)")) {
				append = true;
			}
		}

		return append;
	}

	protected Element addSiblings(Element node, double nodeScore) {
		/**
		 * Now that we have the top candidate, look through its siblings for
		 * content that might also be related. Things like preambles, content
		 * split by ads that we removed, etc.
		 */
		// int baselineScoreForSiblingParagraphs =
		// AddSiblings.getBaselineScoreForSiblings(node);
		double siblingScoreThreshold = Math.max(nodeScore * 0.2, 10);

		Element currentSibling = node.previousElementSibling();
		while (currentSibling != null) {
			boolean append = qualifyToAppendSibling(node, currentSibling,
					siblingScoreThreshold);
			if (append) {

				if (!currentSibling.tagName().toUpperCase().equals("P")
						|| !currentSibling.tagName().toUpperCase()
								.equals("DIV")) {
					// TODO: should change to P if the sibling is not P or DIV
				} else {
					node.child(0).before(currentSibling.outerHtml());
				}
			}
			currentSibling = currentSibling.previousElementSibling();
		}

		// ----
		Element nextSibling = node.nextElementSibling();
		// logger.debug("Next element sibling : "+nextSibling);
		if (nextSibling != null) {
			// Elements iframeElements = nextSibling.getElementsByTag("iframe");
//			Elements iframeElements = nextSibling.select("iframe|object");
//			if (iframeElements.size() > 0) {
//				for (Element iframe : iframeElements) {
//					if (iframe.tagName().equals("iframe")) {
//						String srcAttribute = iframe.attr("src");
//						if (!string.isNullOrEmpty(srcAttribute)) {
//							if (Patterns.exists(Patterns.VIDEOS, srcAttribute)) {
//								logger.debug("Ifarme match found and its a video");
//								node.appendElement("p").appendChild(iframe);
//							}
//						}
//					}
//					if (iframe.tagName().equals("object")) {
//						Elements embedElements = iframe
//								.getElementsByTag("embed");
//						for (Element embedElement : embedElements) {
//							String embedSrc = embedElement.attr("src");
//							if (Patterns.exists(Patterns.VIDEOS, embedSrc)) {
//								logger.debug("Embed Video match found in Next Sibiling");
//								node.appendElement("p").appendChild(iframe);
//							}
//
//						}
//					}
//
//				}
//			}
			boolean append = qualifyToAppendSibling(node, nextSibling,
					siblingScoreThreshold);
			if (append) {

				if (!nextSibling.tagName().toUpperCase().equals("P")
						|| !nextSibling.tagName().toUpperCase()
								.equals("DIV")) {
					// TODO: should change to P if the sibling is not P or DIV
				} else {
					node.child(0).before(nextSibling.outerHtml());
				}
			}
			nextSibling = node.nextElementSibling();
		}

		return node;
	}

	/**
	 * Get the density of links as a percentage of the content This is the
	 * amount of text that is inside a link divided by the total text in the
	 * node.
	 * 
	 * @param DOMElement
	 *            $e
	 * @return number (double)
	 */
	public double getLinkDensity(Element element) {
		double result = 0;
		Elements linkTags = element.getElementsByTag("a");
		int textLength = element.text().length();
		int linkLength = 0;
		for (Element linkTag : linkTags) {
			linkLength += linkTag.text().length();
		}
		if (textLength > 0) {
			result = linkLength / textLength;
		}
		return result;
	}

}
