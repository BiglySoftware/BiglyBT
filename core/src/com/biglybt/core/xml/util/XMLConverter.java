/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.xml.util;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Simple XML to Map converter that mimics output of JSONJava's XML.toJSONObject
 */
public class XMLConverter
{
	private static final boolean ADD_ATTRIBUTES_FIRST = true;

	public static Map<String, Object> xmlToMap(byte[] bytes)
			throws IOException, SAXException, ParserConfigurationException {
		return xmlToMap(new ByteArrayInputStream(bytes));
	}

	public static Map<String, Object> xmlToMap(InputStream inputStream)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// May need to tweak factory here, perhaps to ignore comments, etc
		return xmlToMap(factory.newDocumentBuilder().parse(inputStream));
	}

	public static Map<String, Object> xmlToMap(Node doc) {
		return xmlToMap(doc.getChildNodes(), null);
	}

	public static Map<String, Object> xmlToMap(NodeList nodelist,
			Map<String, Object> toMapOrNull) {
		Map<String, Object> map = toMapOrNull == null ? new HashMap<>()
				: toMapOrNull;

		for (int i = 0; i < nodelist.getLength(); i++) {
			Node node = nodelist.item(i);
			if (node == null) {
				continue;
			}
			String nodeName = node.getNodeName();
			boolean hasAttributes = node.hasAttributes();
			boolean hasChildNodes = node.hasChildNodes();

			if (!hasAttributes && !hasChildNodes) {
				continue;
			}

			Map<String, Object> existing = null;

			if (hasAttributes && ADD_ATTRIBUTES_FIRST) {
				existing = attributesToMap(map, node, nodeName, null);
			}

			if (hasChildNodes) {
				NodeList childNodes = node.getChildNodes();
				boolean goDeeper = true;
				if (childNodes.getLength() == 1) {
					Node firstChildNode = childNodes.item(0);
					if (firstChildNode.getNodeType() == Node.TEXT_NODE) {
						String firstChildNodeVal = firstChildNode.getNodeValue();
						if (hasAttributes) {
							Map<String, Object> mapContent = existing == null
									? new HashMap<>() : existing;
							mapContent.put("content", coerseString(firstChildNodeVal));
							if (existing == null) {
								addValueToMap(map, nodeName, mapContent);
							}
							if (!ADD_ATTRIBUTES_FIRST) {
								existing = mapContent;
							}
						} else {
							addValueToMap(map, nodeName, firstChildNodeVal);
						}
						goDeeper = false;
					}
				}

				if (goDeeper) {
					Map<String, Object> valMap = xmlToMap(childNodes, existing);
					if (existing == null) {
						addValueToMap(map, nodeName, valMap);
					}
					if (!ADD_ATTRIBUTES_FIRST) {
						existing = valMap;
					}
				}
			}

			if (hasAttributes && !ADD_ATTRIBUTES_FIRST) {
				attributesToMap(map, node, nodeName, existing);
			}

		}
		return map;
	}

	private static Map<String, Object> attributesToMap(
			Map<String, Object> mapParent, Node node, String nodeName,
			Map<String, Object> toMapOrNull) {
		// Assumed node.hasAttributes()
		NamedNodeMap attributes = node.getAttributes();
		if (attributes == null) {
			return toMapOrNull;
		}
		Map<String, Object> mapAttributes = toMapOrNull == null ? new HashMap<>()
				: toMapOrNull;
		for (int i = 0; i < attributes.getLength(); i++) {
			Node item = attributes.item(i);
			addValueToMap(mapAttributes, item.getNodeName(), item.getNodeValue());
		}

		if (toMapOrNull == null) {
			addValueToMap(mapParent, nodeName, mapAttributes);
		}
		return mapAttributes;
	}

	private static void addValueToMap(Map<String, Object> map, String key,
			Object val) {
		if (val instanceof String) {
			val = coerseString((String) val);
		}
		if (map.containsKey(key)) {
			Object oldVal = map.get(key);
			if (oldVal instanceof Collection) {
				//noinspection unchecked,rawtypes
				((Collection) oldVal).add(val);
			} else {
				//noinspection unchecked,rawtypes
				map.put(key, new ArrayList(Arrays.asList(oldVal, val)));
			}
		} else {
			map.put(key, val);
		}
	}

	private static Object coerseString(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		if (s.equalsIgnoreCase("true")) {
			return true;
		}
		if (s.equalsIgnoreCase("false")) {
			return false;
		}

		char c = s.charAt(0);
		boolean isLikelyValid;
		int startPos = 1;
		if (c == '-' && s.length() > 1) {
			char next = s.charAt(1);
			startPos++;
			isLikelyValid = next >= '0' && next <= '9';
		} else {
			isLikelyValid = c >= '0' && c <= '9';
		}

		if (isLikelyValid) {
			boolean hasDot = false;
			for (int i = startPos; isLikelyValid && i < s.length(); i++) {
				c = s.charAt(i);
				if (c == '.') {
					isLikelyValid = !hasDot; // two dots bad
					hasDot = true;
				} else {
					isLikelyValid = c >= '0' && c <= '9' || c == '-' || c == 'e'
							|| c == 'E';
				}
			}
			if (isLikelyValid) {
				try {
					if (hasDot) {
						return Double.parseDouble(s);
					}
					return Long.parseLong(s);
				} catch (NumberFormatException ignore) {
					// hopefully never occurs
				}
			}
		}

		return s;
	}
}
