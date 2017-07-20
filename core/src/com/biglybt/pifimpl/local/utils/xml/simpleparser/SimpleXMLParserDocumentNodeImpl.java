/*
 * File    : SimpleXMLParserDocumentNodeImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.pifimpl.local.utils.xml.simpleparser;

import java.io.PrintWriter;
import java.util.Vector;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentAttribute;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;


public class
SimpleXMLParserDocumentNodeImpl
	implements SimpleXMLParserDocumentNode
{
	protected SimpleXMLParserDocumentImpl		document;
	protected Node					node;

	protected SimpleXMLParserDocumentNode[]		kids;

		// node is an ELEMENT_NODE

	protected
	SimpleXMLParserDocumentNodeImpl(
		SimpleXMLParserDocumentImpl	_doc,
		Node			_node )
	{
		document		= _doc;
		node			= _node;
	}

	protected Node
	getNode()
	{
		return( node );
	}

	@Override
	public String
	getName()
	{
		return( node.getLocalName());
	}

	@Override
	public String
	getFullName()
	{
		return( node.getNodeName());
	}

	@Override
	public String
	getNameSpaceURI()
	{
		return( node.getNamespaceURI());
	}

	@Override
	public String
	getValue()
	{
	//	if ( getChildren().length > 0 ){
	//
	//		return( null);

		if ( node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE ){

			return( node.getNodeValue());
		}

		String	res = "";

        for (Node child = node.getFirstChild(); child != null;child = child.getNextSibling()){

            int	type = child.getNodeType();

			if ( type == Node.CDATA_SECTION_NODE ||
				 type == Node.TEXT_NODE ||
				 type == Node.NOTATION_NODE ){

				String str = child.getNodeValue();

				res += str;
			}
        }

		return( res );
	}

	@Override
	public SimpleXMLParserDocumentAttribute
	getAttribute(
		String		name )
	{
		SimpleXMLParserDocumentAttribute[]	attributes = getAttributes();

		for (int i=0;i<attributes.length;i++){

			if ( attributes[i].getName().equalsIgnoreCase( name )){

				return( attributes[i] );
			}
		}

		return( null );
	}

	@Override
	public SimpleXMLParserDocumentAttribute[]
	getAttributes()
	{
		Vector	v = new Vector();

			// for element nodes the attributes AREN'T child elements, rather they are
			// accessed via "getAttributes"

		if ( node.getNodeType() == Node.ELEMENT_NODE ){

			NamedNodeMap atts = node.getAttributes();

            for (int i = 0; i < atts.getLength(); i++){

                Node child = atts.item(i);

				v.addElement( new SimpleXMLParserDocumentAttributeImpl( child.getNodeName(), child.getNodeValue()));
            }
		}

        for (Node child = node.getFirstChild(); child != null;child = child.getNextSibling()){

            int	type = child.getNodeType();

			if ( type == Node.ATTRIBUTE_NODE ){

				v.addElement( new SimpleXMLParserDocumentAttributeImpl( child.getNodeName(), child.getNodeValue()));
			}
		}

		SimpleXMLParserDocumentAttributeImpl[]	res = new SimpleXMLParserDocumentAttributeImpl[v.size()];

		v.copyInto( res );

		return( res );
	}


	@Override
	public SimpleXMLParserDocumentNode[]
	getChildren()
	{
		if ( kids == null ){

			kids = document.parseNode(node,true);
		}

		return( kids );
	}

	@Override
	public SimpleXMLParserDocumentNode
	getChild(
		String		name )
	{
		SimpleXMLParserDocumentNode[]	kids = getChildren();

		for (int i=0;i<kids.length;i++){

			if ( kids[i].getName().equalsIgnoreCase( name )){

				return( kids[i] );
			}
		}

		return( null );
	}

	@Override
	public void
	print()
	{
		PrintWriter	pw = new PrintWriter( System.out );

		print( pw );

		pw.flush();
	}

	@Override
	public void
	print(
		PrintWriter	pw )
	{
		print( pw, "" );
	}

	protected void
	print(
		PrintWriter	pw,
		String		indent )
	{
		String	attr_str = "";

		SimpleXMLParserDocumentAttribute[]	attrs = getAttributes();

		for (int i=0;i<attrs.length;i++){
			attr_str += (i==0?"":",")+attrs[i].getName() + "=" + attrs[i].getValue();
		}

		pw.println( indent + getName() + ":" + attr_str + " -> " + getValue());

		SimpleXMLParserDocumentNode[]	kids = getChildren();

		for (int i=0;i<kids.length;i++){

			((SimpleXMLParserDocumentNodeImpl)kids[i]).print( pw, indent + "  " );
		}
	}


	/*
        switch (type) {
        case Node.ATTRIBUTE_NODE:
            out.print("ATTR:");
            printlnCommon(n);
            break;
        case Node.CDATA_SECTION_NODE:
            out.print("CDATA:");
            printlnCommon(n);
            break;
        case Node.COMMENT_NODE:
            out.print("COMM:");
            printlnCommon(n);
            break;
        case Node.DOCUMENT_FRAGMENT_NODE:
            out.print("DOC_FRAG:");
            printlnCommon(n);
            break;
        case Node.DOCUMENT_NODE:
            out.print("DOC:");
            printlnCommon(n);
            break;
        case Node.DOCUMENT_TYPE_NODE:
            out.print("DOC_TYPE:");
            printlnCommon(n);

            // Print entities if any
            NamedNodeMap nodeMap = ((DocumentType)n).getEntities();
            indent += 2;
            for (int i = 0; i < nodeMap.getLength(); i++) {
                Entity entity = (Entity)nodeMap.item(i);
                echo(entity);
            }
            indent -= 2;
            break;
        case Node.ELEMENT_NODE:
            out.print("ELEM:");
            printlnCommon(n);

            // Print attributes if any.  Note: element attributes are not
            // children of ELEMENT_NODEs but are properties of their
            // associated ELEMENT_NODE.  For this reason, they are printed
            // with 2x the indent level to indicate this.
            NamedNodeMap atts = n.getAttributes();
            indent += 2;
            for (int i = 0; i < atts.getLength(); i++) {
                Node att = atts.item(i);
                echo(att);
            }
            indent -= 2;
            break;
        case Node.ENTITY_NODE:
            out.print("ENT:");
            printlnCommon(n);
            break;
        case Node.ENTITY_REFERENCE_NODE:
            out.print("ENT_REF:");
            printlnCommon(n);
            break;
        case Node.NOTATION_NODE:
            out.print("NOTATION:");
            printlnCommon(n);
            break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            out.print("PROC_INST:");
            printlnCommon(n);
            break;
        case Node.TEXT_NODE:
            out.print("TEXT:");
            printlnCommon(n);
            break;
        default:
            out.print("UNSUPPORTED NODE: " + type);
            printlnCommon(n);
            break;
        }

        // Print children if any
        indent++;
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNextSibling()) {
            echo(child);
        }
        indent--;
	}
*/
}
