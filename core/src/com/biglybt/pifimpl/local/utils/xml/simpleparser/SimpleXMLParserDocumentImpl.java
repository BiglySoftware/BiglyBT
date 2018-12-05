/*
 * File    : SimpleXMLParserDocumentImpl.java
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

import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang.Entities;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.*;

import com.biglybt.core.util.*;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocument;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentAttribute;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentException;
import com.biglybt.pif.utils.xml.simpleparser.SimpleXMLParserDocumentNode;

public class
SimpleXMLParserDocumentImpl
	implements SimpleXMLParserDocument
{
	private static DocumentBuilderFactory 		dbf_singleton;

	private URL			source_url;

	private Document						document;
	private SimpleXMLParserDocumentNodeImpl	root_node;


	public
	SimpleXMLParserDocumentImpl(
		File		file )

		throws SimpleXMLParserDocumentException
	{
		try{

			create( new FileInputStream( file ));

		}catch( Throwable e ){

			throw( new SimpleXMLParserDocumentException( e ));
		}
	}

	public
	SimpleXMLParserDocumentImpl(
		String		data )

		throws SimpleXMLParserDocumentException
	{
		try{
			create( new ByteArrayInputStream( data.getBytes( Constants.DEFAULT_ENCODING )));

		}catch( UnsupportedEncodingException e ){

		}
	}

	public
	SimpleXMLParserDocumentImpl(
		URL				_source_url,
		InputStream		_input_stream )

		throws SimpleXMLParserDocumentException
	{
		source_url		= _source_url;

		create( _input_stream );
	}

	protected static synchronized DocumentBuilderFactory
	getDBF()
	{
			// getting the factory involves a fait bit of work - cache it

		if ( dbf_singleton == null ){

			dbf_singleton = DocumentBuilderFactory.newInstance();

			// Set namespaceAware to true to get a DOM Level 2 tree with nodes
			// containing namesapce information.  This is necessary because the
			// default value from JAXP 1.0 was defined to be false.

			dbf_singleton.setNamespaceAware(true);

			// Set the validation mode to either: no validation, DTD
			// validation, or XSD validation

			dbf_singleton.setValidating( false );

			// Optional: set various configuration options

			dbf_singleton.setIgnoringComments(true);
			dbf_singleton.setIgnoringElementContentWhitespace(true);
			dbf_singleton.setCoalescing(true);

			// The opposite of creating entity ref nodes is expanding them inline
			// NOTE that usage of, e.g. "&amp;" in text results in an entity ref. e.g.
			//	if ("BUY".equals (type) "
			//		ENT_REF: nodeName="amp"
			//		TEXT: nodeName="#text" nodeValue="&"

			dbf_singleton.setExpandEntityReferences(true);
		}

		return( dbf_singleton );
	}

	private void
	create(
		InputStream		_input_stream )

		throws SimpleXMLParserDocumentException
	{
			// make sure we can mark the stream to permit later recovery if needed

		if ( !_input_stream.markSupported()){

			_input_stream = new BufferedInputStream( _input_stream );
		}

		_input_stream.mark( 100*1024 );

			// prevent the parser from screwing with our stream by closing it

		UncloseableInputStream	uc_is = new UncloseableInputStream( _input_stream );

		SimpleXMLParserDocumentException error = null;

		try{
			createSupport( uc_is );

		}catch( SimpleXMLParserDocumentException e ){

			String msg = Debug.getNestedExceptionMessage( e );

			if (	( msg.contains( "entity" ) && msg.contains( "was referenced" )) ||
					msg.contains( "entity reference" )){

				try{
						// nasty hack to try and handle HTML entities that some annoying feeds include :(

					_input_stream.reset();

					createSupport( new EntityFudger( _input_stream ));

					return;

				}catch( Throwable f ){

					if ( f instanceof SimpleXMLParserDocumentException ){

						error = (SimpleXMLParserDocumentException)f;
					}
				}
			}

			if ( error == null ){

				error = e;
			}

			throw( error );

		}finally{

			if ( Constants.isCVSVersion() && error != null ){

				try{
					_input_stream.reset();

					String stuff = FileUtil.readInputStreamAsStringWithTruncation( _input_stream, 2014 );

					Debug.out( "RSS parsing failed for '" + stuff + "': " + Debug.getExceptionMessage( error ));

				}catch( Throwable e ){
				}
			}
			try{
				_input_stream.close();

			}catch( Throwable e ){
			}
		}
	}

	private void
	createSupport(
		InputStream		input_stream )

		throws SimpleXMLParserDocumentException
	{
		try{
			DocumentBuilderFactory dbf = getDBF();

			// Step 2: create a DocumentBuilder that satisfies the constraints
			// specified by the DocumentBuilderFactory

			DocumentBuilder db = dbf.newDocumentBuilder();

			// Set an ErrorHandler before parsing

			OutputStreamWriter errorWriter = new OutputStreamWriter(System.err);

			MyErrorHandler error_handler = new MyErrorHandler(new PrintWriter(errorWriter, true));

			db.setErrorHandler( error_handler );

			db.setEntityResolver(
				new EntityResolver()
				{
					@Override
					public InputSource
					resolveEntity(
						String publicId, String systemId )
					{
						// System.out.println( publicId + ", " + systemId );

						// handle bad DTD external refs

						try{
							URL url  = new URL( systemId );

							String protocol = url.getProtocol();
							
							if ( !protocol.toLowerCase().startsWith( "http" )){
								
								return( new InputSource( new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes())));
							}
							
							if ( source_url != null ){

								String net = AENetworkClassifier.categoriseAddress( source_url.getHost());

								if ( net != AENetworkClassifier.AT_PUBLIC ){

									if ( AENetworkClassifier.categoriseAddress( url.getHost()) != net ){

										return new InputSource(	new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes()));
									}
								}
							}

							String host = url.getHost();

							InetAddress.getByName( host );

								// try connecting too as connection-refused will also bork XML parsing

							InputStream is = null;

							try{
								URLConnection con = url.openConnection();

								con.setConnectTimeout( 15*1000 );
								con.setReadTimeout( 15*1000 );

								is = con.getInputStream();

								byte[]	buffer = new byte[32];

								int	pos = 0;

								while( pos < buffer.length ){

									int len = is.read( buffer, pos, buffer.length - pos );

									if ( len <= 0 ){

										break;
									}

									pos += len;
								}

								String str = new String( buffer, "UTF-8" ).trim().toLowerCase( Locale.US );

								if ( !str.contains( "<?xml" )){

										// not straightforward to check for naked DTDs, could be lots of <!-- commentry preamble which of course can occur
										// in HTML too

									buffer = new byte[32000];

									pos = 0;

									while( pos < buffer.length ){

										int len = is.read( buffer, pos, buffer.length - pos );

										if ( len <= 0 ){

											break;
										}

										pos += len;
									}

									str += new String( buffer, "UTF-8" ).trim().toLowerCase( Locale.US );

									if ( str.contains( "<html") && str.contains( "<head" )){

										throw( new Exception( "Bad DTD" ));
									}
								}
							}catch( Throwable e ){

								return new InputSource(	new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes()));

							}finally{

								if ( is != null ){

									try{
										is.close();

									}catch( Throwable e){

									}
								}
							}
							return( null );

						}catch( UnknownHostException e ){

							return new InputSource(	new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes()));

						}catch( Throwable e ){

							return( null );
						}
					}
				});

			// Step 3: parse the input file

			document = db.parse( input_stream );

			SimpleXMLParserDocumentNodeImpl[] root_nodes = parseNode( document, false );

			int	root_node_count	= 0;

				// remove any processing instructions such as <?xml-stylesheet

			for (int i=0;i<root_nodes.length;i++){

				SimpleXMLParserDocumentNodeImpl	node = root_nodes[i];

				if ( node.getNode().getNodeType() != Node.PROCESSING_INSTRUCTION_NODE ){

					root_node	= node;

					root_node_count++;
				}
			}

			if ( root_node_count != 1 ){

				throw( new SimpleXMLParserDocumentException( "invalid document - " + root_nodes.length + " root elements" ));
			}

		}catch( Throwable e ){

			throw( new SimpleXMLParserDocumentException( e ));
		}
	}

	@Override
	public String
	getName()
	{
		return( root_node.getName());
	}

	@Override
	public String
	getFullName()
	{
		return( root_node.getFullName());
	}

	@Override
	public String
	getNameSpaceURI()
	{
		return( root_node.getNameSpaceURI());
	}

	@Override
	public String
	getValue()
	{
		return( root_node.getValue());
	}

	@Override
	public SimpleXMLParserDocumentNode[]
	getChildren()
	{
		return( root_node.getChildren());
	}
	@Override
	public SimpleXMLParserDocumentNode
	getChild(
		String	name )
	{
		return( root_node.getChild(name));
	}

	@Override
	public SimpleXMLParserDocumentAttribute[]
	getAttributes()
	{
		return( root_node.getAttributes());
	}
	@Override
	public SimpleXMLParserDocumentAttribute
	getAttribute(
		String		name )
	{
		return( root_node.getAttribute(name));
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
		root_node.print( pw, "" );
	}

		// idea is to flatten out any unwanted structure. We just want the resultant
		// tree to have nodes for each nesting element and leaves denoting name/value bits

	protected SimpleXMLParserDocumentNodeImpl[]
	parseNode(
		Node		node,
		boolean		skip_this_node )
	{
        int type = node.getNodeType();

		if ( (	type == Node.ELEMENT_NODE ||
				type == Node.PROCESSING_INSTRUCTION_NODE )&& !skip_this_node ){

			return( new SimpleXMLParserDocumentNodeImpl[]{ new SimpleXMLParserDocumentNodeImpl( this, node )});
		}

		Vector	v = new Vector();

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()){

			SimpleXMLParserDocumentNodeImpl[] kids = parseNode( child, false );

			for (int i=0;i<kids.length;i++){

				v.addElement(kids[i]);
			}
        }

		SimpleXMLParserDocumentNodeImpl[]	res = new SimpleXMLParserDocumentNodeImpl[v.size()];

		v.copyInto( res );

		return( res );
	}

    private static class MyErrorHandler implements ErrorHandler {
        /** Error handler output goes here */
        //private PrintWriter out;

        MyErrorHandler(PrintWriter out) {
            //this.out = out;
        }

        /**
         * Returns a string describing parse exception details
         */
        private String getParseExceptionInfo(SAXParseException spe) {
            String systemId = spe.getSystemId();
            if (systemId == null) {
                systemId = "null";
            }
            String info = "URI=" + systemId +
                " Line=" + spe.getLineNumber() +
                ": " + spe.getMessage();
            return info;
        }

        // The following methods are standard SAX ErrorHandler methods.
        // See SAX documentation for more info.

        @Override
        public void
		warning(
			SAXParseException spe )

			throws SAXException
		{
            // out.println("Warning: " + getParseExceptionInfo(spe));
        }

        @Override
        public void
		error(
			SAXParseException spe )

			throws SAXException
		{
            String message = "Error: " + getParseExceptionInfo(spe);

            throw new SAXException(message);
        }

        @Override
        public void
		fatalError(
			SAXParseException spe )

			throws SAXException
		{
            String message = "Fatal Error: " + getParseExceptionInfo(spe);

            throw new SAXException(message,spe);
        }
    }

    private static class
    EntityFudger
    	extends InputStream
    {
    	private InputStream		is;

    	char[]	buffer		= new char[16];
    	int		buffer_pos	= 0;

    	char[] 	insertion		= new char[16];
    	int		insertion_pos	= 0;
    	int		insertion_len	= 0;

    	public
    	EntityFudger(
    		InputStream		_is )
    	{
    		is		= _is;
    	}

    	@Override
    	public int
    	read()
    		throws IOException
    	{
    		if ( insertion_len > 0 ){

    			int	result = insertion[ insertion_pos++ ]&0xff;

    			if ( insertion_pos == insertion_len ){

     				insertion_pos	= 0;
     				insertion_len	= 0;
    			}

    			return( result );
    		}

    		while( true ){

	     		int	b = is.read();

	     		if ( b < 0 ){

	     				// end of file

	     			if ( buffer_pos == 0 ){

	     				return( b );

	     			}else if ( buffer_pos == 1 ){

	     				buffer_pos = 0;

	     				return( buffer[0]&0xff );

	     			}else{

	     				System.arraycopy( buffer, 1, insertion, 0, buffer_pos - 1 );

	     				insertion_len 	= buffer_pos - 1;
	     				insertion_pos	= 0;

	     				buffer_pos = 0;

	     				return( buffer[0]&0xff );
	     			}
	     		}

	     			// normal byte

	     		if ( buffer_pos == 0 ){

	     			if ( b == '&' ){

	     				buffer[ buffer_pos++ ] = (char)b;

	     			}else{

	     				return( b );
	     			}

	     		}else{

	     			if ( buffer_pos == buffer.length-1 ){

	     					// buffer's full, give up

	     				buffer[ buffer_pos++ ] = (char)b;

	     				System.arraycopy( buffer, 0, insertion, 0, buffer_pos );

	     				buffer_pos		= 0;
	     				insertion_pos	= 0;
	     				insertion_len	= buffer_pos;

	     				return( insertion[insertion_pos++] );

	     			}else{

		     			if ( b == ';' ){

		     					// got some kind of reference mebe

		     				buffer[ buffer_pos++ ] = (char)b;

		     				String	ref = new String( buffer, 1, buffer_pos-2 ).toLowerCase( Locale.US );

		     				String	replacement;

		     				if ( 	ref.equals( "amp") 		||
		     						ref.equals( "lt" ) 		||
		     						ref.equals( "gt" ) 		||
		     						ref.equals( "quot" )	||
		     						ref.equals( "apos" ) 	||
		     						ref.startsWith( "#" )){

		     					replacement = new String( buffer, 0, buffer_pos );

		     				}else{

			     				int num = Entities.HTML40.entityValue( ref );

		     					if ( num != -1 ){

		     						replacement = "&#" + num + ";";

		     					}else{

		     						replacement = new String( buffer, 0, buffer_pos );
		     					}
		     				}

		     				char[] chars = replacement.toCharArray();

		     				System.arraycopy( chars, 0, insertion, 0, chars.length );

		     				buffer_pos		= 0;
		     				insertion_pos	= 0;
		     				insertion_len	= chars.length;

		     				return( insertion[insertion_pos++] );

		     			}else{

	     					buffer[ buffer_pos++ ] = (char)b;

		     				char c = (char)b;

		     				if ( !Character.isLetterOrDigit( c )){

		     						// handle naked &

		     					if ( buffer_pos == 2 && buffer[0] == '&'){

		     						char[] chars = "&amp;".toCharArray();

		     						System.arraycopy( chars, 0, insertion, 0, chars.length );

		     						buffer_pos		= 0;
		     						insertion_pos	= 0;
		     						insertion_len	= chars.length;

		     							// don't forget the char we just read

		     						insertion[insertion_len++] = (char)b;

		     						return( insertion[insertion_pos++] );

		     					}else{

		     							// not a valid entity reference

		    	     				System.arraycopy( buffer, 0, insertion, 0, buffer_pos );

		    	     				buffer_pos		= 0;
		    	     				insertion_pos	= 0;
		    	     				insertion_len	= buffer_pos;

		    	     				return( insertion[insertion_pos++] );
		     					}
		     				}
		     			}
	     			}
	     		}
    		}
    	}

    	@Override
	    public void
    	close()

    		throws IOException
    	{
    		is.close();
    	}

    	@Override
	    public long
    	skip(
    		long n )

    		throws IOException
    	{
    			// meh, vague attempt here

    		if ( insertion_len > 0 ){

    				// buffer is currently empty, shove remaining into buffer to unify processing

    			int	rem = insertion_len - insertion_pos;

    			System.arraycopy( insertion, insertion_pos, buffer, 0, rem );

    			insertion_pos 	= 0;
    			insertion_len	= 0;

    			buffer_pos = rem;
    		}

    		if ( n <= buffer_pos ){

    				// skip is <= buffer contents

    			int	rem = buffer_pos - (int)n;

      			System.arraycopy( buffer, (int)n, insertion, 0, rem );

      			insertion_pos	= 0;
      			insertion_len 	= rem;

      			return( n );
    		}

    		int	to_skip = buffer_pos;

    		buffer_pos	= 0;

    		return( is.skip( n - to_skip ) + to_skip );
    	}

    	@Override
	    public int
    	available()

    		throws IOException
    	{
     		return( buffer_pos + is.available());
    	}
    }
}
