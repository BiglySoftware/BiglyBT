/*
 * Written and copyright 2001-2003 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 *
 * Set.java
 *
 * Created on 23.03.2004
 *
 */
package com.biglybt.ui.console.commands;

import java.io.PrintStream;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.util.SHA1Hasher;
import com.biglybt.ui.common.ExternalUIConst;
import com.biglybt.ui.common.util.StringPattern;
import com.biglybt.ui.console.ConsoleConfigSections;
import com.biglybt.ui.console.ConsoleConfigSections.ParameterWithConfigSection;
import com.biglybt.ui.console.ConsoleInput;
import com.biglybt.ui.console.util.PrintUtils;


/**
 * command that allows manipulation of the client's runtime properties.
 * - when called without any parameters, it lists all of the available runtime properties.
 * - when called with 1 parameter, it shows the current value of that parameter
 * - when called with 2 or 3 parameters, it assigns a specified value to the
 *   specified parameter name. (the third parameter forces the property to be set
 *   to a particular type, otherwise we try and guess the type by the current value)
 * @author Tobias Minich, Paul Duran
 */
public class Set extends IConsoleCommand {

	private static final String NULL_STRING = "__NULL__";

	public Set()
	{
		super("set", "+");
	}

	@Override
	public String getCommandDescriptions() {
		return("set [options] [parameter] [value]\t\t+\tSet a configuration parameter. Use \"param name\" when the name includes a space. If value is omitted, the current setting is shown. Parameter may be a wildcard to narrow results");
	}
	@Override
	public void printHelpExtra(PrintStream out, List args) {
		out.println("> -----");
		out.println("'set' common parameter abbreviations: ");
		out.println("\tmax_up: Maximum upload speed in KB/sec" );		// see ExternalUIConst for the mappings for these
		out.println("\tmax_down: Maximum download speed in KB/sec" );
		out.println("'set' options: ");
		out.println("\t-export\t\tPrints all the options with non-defaut values.");
	}

	@Override
	public void execute(String commandName, ConsoleInput ci, List args) {

		boolean		non_defaults = false;

		Iterator	it = args.iterator();

		while( it.hasNext()){
			String	arg = (String)it.next();
			if ( arg.equals( "-export" )){
				non_defaults = true;
				it.remove();
			}
		}
		if( args.isEmpty() )
		{
			displayOptions(ci.out, new StringPattern("*"), non_defaults );
			return;
		}
		String external_name = (String) args.get(0);
		String internal_name = (String) ExternalUIConst.parameterlegacy.get(external_name);
		if( internal_name == null || internal_name.length() == 0 )
		{
			internal_name = external_name;
		}
//		else
//			ci.out.println("> converting " + origParamName + " to " + parameter);

		switch( args.size() )
		{
			case 1:
				// allow wildcards : eg: Core* or *DHT* to shorten result list
				StringPattern sp = new StringPattern(internal_name);
				if( sp.hasWildcard() )
				{
					displayOptions(ci.out, sp, non_defaults);
				}
				else
				{
					ParameterWithConfigSection paramInfo = ConsoleConfigSections.getInstance().getParameter(internal_name);
					if (paramInfo != null) {
						PrintUtils.printParam(ci.out, paramInfo.configSection, true, paramInfo.parameter, false);
						break;
					}

					// try to display the value of the specified parameter
					if( ! COConfigurationManager.doesParameterDefaultExist( internal_name ) )
					{
						ci.out.println("> Command 'set': Parameter '" + external_name + "' unknown.");
						return;
					}


					ParameterDeprecated param = ParameterDeprecated.get(internal_name,external_name);

					ci.out.println( param.getString( false ) );
				}
				break;
			case 2:
			case 3:
				String setto = (String) args.get(1);
				String type;
				if( args.size() == 2 )
				{
					// guess the parameter type by getting the current value and determining its type
					ParameterDeprecated param = ParameterDeprecated.get( internal_name, external_name );
					type = param.getType();
				}
				else
					type = (String) args.get(2);

				boolean success = false;
				if( type.equalsIgnoreCase("int") || type.equalsIgnoreCase("integer") ) {
					COConfigurationManager.setParameter( internal_name, Integer.parseInt( setto ) );
					success = true;
				}
				else if( type.equalsIgnoreCase("bool") || type.equalsIgnoreCase("boolean") ) {

					boolean	value;

					if ( setto.equalsIgnoreCase("true") || setto.equalsIgnoreCase("y") || setto.equals("1" )){
						value = true;
					}else{
						value = false;
					}

					COConfigurationManager.setParameter( internal_name, value );
					success = true;
				}
				else if( type.equalsIgnoreCase("float") ) {
					COConfigurationManager.setParameter( internal_name, Float.parseFloat( setto ) );
					success = true;
				}
				else if( type.equalsIgnoreCase("string") ) {
					COConfigurationManager.setParameter( internal_name, setto );
					success = true;
				}
				else if( type.equalsIgnoreCase("password") ) {
					SHA1Hasher hasher = new SHA1Hasher();

					byte[] password = setto.getBytes();

					byte[] encoded;

					if(password.length > 0){

						encoded = hasher.calculateHash(password);

					}else{

						encoded = password;
					}

					COConfigurationManager.setParameter( internal_name, encoded );

					success = true;
				}

				if( success ) {
					COConfigurationManager.save();
					ci.out.println("> Parameter '" + external_name + "' set to '" + setto + "'. [" + type + "]");

					ParameterWithConfigSection paramInfo = ConsoleConfigSections.getInstance().getParameter(
							internal_name);
					if (paramInfo != null) {
						PrintUtils.printParam(ci.out, paramInfo.configSection, true,
								paramInfo.parameter, false);
					}
				}
				else ci.out.println("ERROR: invalid type given");

				break;
			default:
				ci.out.println("Usage: 'set \"parameter\" value type', where type = int, bool, float, string, password");
				break;
		}
	}

	private void displayOptions(PrintStream out, StringPattern sp, boolean non_defaults)
	{
		sp.setIgnoreCase(true);
		Iterator I = non_defaults?COConfigurationManager.getDefinedParameters().iterator():COConfigurationManager.getAllowedParameters().iterator();
		Map backmap = new HashMap();
		for (Iterator iter = ExternalUIConst.parameterlegacy.entrySet().iterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			backmap.put( entry.getValue(), entry.getKey() );
		}
		TreeSet srt = new TreeSet();
		while (I.hasNext()) {
			String internal_name = (String) I.next();

			String	external_name = (String) backmap.get(internal_name);

			if ( external_name == null ){

				external_name = internal_name;
			}
			if( sp.matches(external_name) )
			{
				ParameterDeprecated param = ParameterDeprecated.get( internal_name, external_name );

				if ( non_defaults ){

					if ( !param.isDefault()){

						srt.add( param.getString( true ));
					}
				}else{

					srt.add( param.getString( false ));
				}
			}
		}
		I = srt.iterator();
		while (I.hasNext()) {
			out.println((String) I.next());
		}
	}

	/**
	 * class that represents a parameter. we can use one of these objects to
	 * verify a parameter's type and value as well as whether or not a value has been set.
	 * @author pauld
	 */
	private static class ParameterDeprecated
	{
		private static final int PARAM_INT 		= 1;
		private static final int PARAM_BOOLEAN 	= 2;
		private static final int PARAM_STRING 	= 4;
		private static final int PARAM_OTHER 	= 8;

		/**
		 * returns a new Parameter object reprenting the specified parameter name
		 * @param parameter
		 * @return
		 */
		public static ParameterDeprecated
		get(
			String	internal_name,
			String	external_name )
		{
			int underscoreIndex = external_name.indexOf('_');
			int nextchar = external_name.charAt(underscoreIndex + 1);

			if ( 	internal_name != external_name &&
					"ibs".indexOf(nextchar) >= 0 ){

				try {
					if( nextchar == 'i' )
					{
						int value = COConfigurationManager.getIntParameter(internal_name, Integer.MIN_VALUE);
						return new ParameterDeprecated(internal_name, external_name, value == Integer.MIN_VALUE ? (Integer)null : new Integer(value) );
					}
					else if( nextchar == 'b' )
					{
						// firstly get it as an integer to make sure it is actually set to something
						if( COConfigurationManager.getIntParameter(internal_name, Integer.MIN_VALUE) != Integer.MIN_VALUE )
						{
							boolean b = COConfigurationManager.getBooleanParameter(internal_name);
							return new ParameterDeprecated(internal_name, external_name, Boolean.valueOf(b));
						}
						else
						{
							return new ParameterDeprecated(internal_name, external_name, (Boolean)null);
						}
					}
					else
					{
						String value = COConfigurationManager.getStringParameter(internal_name, NULL_STRING);
						return new ParameterDeprecated( internal_name, external_name, NULL_STRING.equals(value) ? null : value);
					}
				} catch (Throwable e){

				}
			}

			Object v = COConfigurationManager.getParameter( internal_name );

			try {
				if ( v instanceof Long || v instanceof Integer ){

					int value = COConfigurationManager.getIntParameter(internal_name, Integer.MIN_VALUE);

					return new ParameterDeprecated(internal_name, external_name, value == Integer.MIN_VALUE ? (Integer)null : new Integer(value) );

				}else if ( v instanceof Boolean ){

					boolean value = COConfigurationManager.getBooleanParameter( internal_name );

					return new ParameterDeprecated( internal_name, external_name, Boolean.valueOf( value ));

				}else if ( v instanceof String || v instanceof byte[] ){

					String value = COConfigurationManager.getStringParameter(internal_name);

					return new ParameterDeprecated( internal_name, external_name, NULL_STRING.equals(value) ? null : value);
				}else{

					return new ParameterDeprecated( internal_name, external_name, v, PARAM_OTHER );
				}
			}catch( Throwable e2 ){

				return new ParameterDeprecated( internal_name, external_name, v, PARAM_OTHER );
			}
		}

		public ParameterDeprecated( String iname, String ename, Boolean val )
		{
			this(iname,ename, val, PARAM_BOOLEAN);
		}
		public ParameterDeprecated( String iname, String ename, Integer val )
		{
			this(iname,ename, val, PARAM_INT);
		}
		public ParameterDeprecated( String iname, String ename, String val )
		{
			this(iname,ename, val, PARAM_STRING);
		}
		private ParameterDeprecated( String _iname, String _ename, Object _val, int _type )
		{
			type = _type;
			iname = _iname;
			ename = _ename;
			value = _val;
			isSet = (value != null);

			if ( !isSet ){

				def = COConfigurationManager.getDefault(iname);

				if  ( def != null ){

					if ( def instanceof Long ){

						type = PARAM_INT;
					}
				}
			}
		}
		private int type;
		private String iname;
		private String ename;
		private Object value;
		private boolean isSet;
		private Object	def;

		public String getType()
		{
			switch( type )
			{
				case PARAM_BOOLEAN:
					return "bool";
				case PARAM_INT:
					return "int";
				case PARAM_STRING:
					return "string";
				default:
					return "unknown";
			}
		}

		public boolean
		isDefault()
		{
			return( !isSet );
		}

		public String
		getString(
			boolean	set_format )
		{
			if( isSet ){
				if ( set_format ){

					return( "set " + quoteIfNeeded( ename ) + " " + quoteIfNeeded(value.toString()) + " " + getType());

				}else{

					return "> " + ename + ": " + value + " [" + getType() + "]";
				}
			}else{
				if ( def == null ){

					return "> " + ename + " is not set. [" + getType() + "]";

				}else{
					return "> " + ename + " is not set. [" + getType() + ", default: " + def + "]";
				}
			}
		}
	}

	protected static String
	quoteIfNeeded(
		String str)
	{
		if ( str.indexOf(' ') == -1 ){

			return( str );
		}

		return( "\"" + str + "\"" );
	}

}
