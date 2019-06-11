/*
 * File    : RPPluginConfig.java
 * Created : 17-Feb-2004
 * By      : parg
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

package com.biglybt.pifimpl.remote;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.biglybt.pif.PluginConfig;
import com.biglybt.pif.PluginConfigListener;
import com.biglybt.pif.PluginException;
import com.biglybt.pif.config.ConfigParameter;
import com.biglybt.pif.config.PluginConfigSource;

/**
 * @author parg
 */

public class
RPPluginConfig
	extends		RPObject
	implements 	PluginConfig
{
	protected transient PluginConfig		delegate;
	protected transient	Properties			property_cache;

		// don't change these field names as they are visible on XML serialisation

	public String[]		cached_property_names;
	public Object[]		cached_property_values;

	public static PluginConfig
	create(
		PluginConfig		_delegate )
	{
		RPPluginConfig	res =(RPPluginConfig)_lookupLocal( _delegate );

		if ( res == null ){

			res = new RPPluginConfig( _delegate );
		}

		return( res );
	}

	protected
	RPPluginConfig(
		PluginConfig		_delegate )
	{
		super( _delegate );
	}

	@Override
	protected void
	_setDelegate(
		Object		_delegate )
	{
		delegate = (PluginConfig)_delegate;

		cached_property_names 	= new String[]{
				CORE_PARAM_INT_MAX_UPLOAD_SPEED_KBYTES_PER_SEC,
				CORE_PARAM_INT_MAX_UPLOAD_SPEED_SEEDING_KBYTES_PER_SEC,
				CORE_PARAM_INT_MAX_DOWNLOAD_SPEED_KBYTES_PER_SEC,
				CORE_PARAM_INT_MAX_CONNECTIONS_PER_TORRENT,
				CORE_PARAM_INT_MAX_CONNECTIONS_GLOBAL,
				CORE_PARAM_INT_MAX_DOWNLOADS,
				CORE_PARAM_INT_MAX_ACTIVE,
				CORE_PARAM_INT_MAX_ACTIVE_SEEDING,
				CORE_PARAM_INT_MAX_UPLOADS,
				CORE_PARAM_INT_MAX_UPLOADS_SEEDING
			};

		cached_property_values 	= new Object[]{
				new Integer( delegate.getCoreIntParameter( cached_property_names[0] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[1] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[2] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[3] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[4] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[5] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[6] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[7] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[8] )),
				new Integer( delegate.getCoreIntParameter( cached_property_names[9] ))
		};
	}

	@Override
	public Object
	_setLocal()

		throws RPException
	{
		return( _fixupLocal());
	}

	@Override
	public void
	_setRemote(
		RPRequestDispatcher		_dispatcher )
	{
		super._setRemote( _dispatcher );

		property_cache	= new Properties();

		for (int i=0;i<cached_property_names.length;i++){

			// System.out.println( "cache:" + cached_property_names[i] + "=" + cached_property_values[i] );

			property_cache.put(cached_property_names[i],cached_property_values[i]);
		}
	}

	@Override
	public RPReply
	_process(
		RPRequest	request	)
	{
		String	method = request.getMethod();

		Object[] params = (Object[])request.getParams();

		if ( method.equals( "getPluginIntParameter[String,int]")){

			return( new RPReply( new Integer( delegate.getPluginIntParameter((String)params[0],((Integer)params[1]).intValue()))));

		}else if ( method.equals( "getPluginStringParameter[String,String]")){

			return( new RPReply( delegate.getPluginStringParameter((String)params[0],(String)params[1])));

		}else if ( method.equals( "setPluginParameter[String,int]")){

			delegate.setPluginParameter((String)params[0],((Integer)params[1]).intValue());

			return( null );

		}else if ( method.equals( "setParameter[String,int]")){

			delegate.setCoreIntParameter((String)params[0],((Integer)params[1]).intValue());

			return( null );

		}else if ( method.equals( "save")){

			try{
				delegate.save();

				return( null );

			}catch( PluginException e ){

				return( new RPReply( e ));
			}
		}


		throw( new RPException( "Unknown method: " + method ));
	}

	// ***************************************************

	@Override
	public boolean
	isNewInstall()
	{
	  	notSupported();

	  	return( false );
	}

	@Override
	public String
	getPluginConfigKeyPrefix()
	{
	  	notSupported();

	  	return("");
	}

	@Override
	  public List
	  getPluginListParameter( String key, List	default_value )
	  {
		  	notSupported();

		  	return( null );
	  }

	  @Override
	  public void
	  setPluginListParameter( String key, List	value )
	  {
		  notSupported();
	  }

	  @Override
	  public Map
	  getPluginMapParameter( String key, Map	default_value )
	  {
		  	notSupported();

		  	return( null );
	  }

	  @Override
	  public void
	  setPluginMapParameter( String key, Map	value )
	  {
		  notSupported();
	  }
	  @Override
	  public int getPluginIntParameter(String key)
	  {
	  	notSupported();

	  	return(0);
	  }

	  @Override
	  public int getPluginIntParameter(String key, int defaultValue)
	  {
		Integer	res = (Integer)_dispatcher.dispatch( new RPRequest( this, "getPluginIntParameter[String,int]", new Object[]{key,new Integer(defaultValue)} )).getResponse();

		return( res.intValue());
	  }

	  @Override
	  public String getPluginStringParameter(String key)
	  {
	  	notSupported();

	  	return(null);
	  }

	  @Override
	  public String getPluginStringParameter(String key, String defaultValue)
	  {
		String	res = (String)_dispatcher.dispatch( new RPRequest( this, "getPluginStringParameter[String,String]", new Object[]{key,defaultValue} )).getResponse();

		return( res );
	  }

	  @Override
	  public boolean getPluginBooleanParameter(String key)
	  {
	  	notSupported();

	  	return(false);
	  }

	  @Override
	  public byte[] getPluginByteParameter(String key, byte[] defaultValue )
	  {
	  	notSupported();

	  	return(null);
	  }

	  @Override
	  public boolean getPluginBooleanParameter(String key, boolean defaultValue)
	  {
	  	notSupported();

	  	return(false);
	  }

	  @Override
	  public void setPluginParameter(String key, int value)
	  {
		_dispatcher.dispatch( new RPRequest( this, "setPluginParameter[String,int]", new Object[]{key,new Integer(value)} ));
	  }

	  @Override
	  public void setPluginParameter(String key, int value, boolean global)
	  {
		  notSupported();
	  }

	  @Override
	  public void setPluginParameter(String key, String value)
	  {

	  	notSupported();
	  }

	  @Override
	  public void setPluginParameter(String key, boolean value)
	  {
	  	notSupported();
	  }

	  @Override
	  public void setPluginParameter(String key, byte[] value)
	  {
	  	notSupported();
	  }

	  @Override
	  public ConfigParameter
	  getParameter(
		String		key )
	  {
	  	notSupported();

	  	return( null );
	  }

	  @Override
	  public ConfigParameter
	  getPluginParameter(
	  		String		key )
	  {
	  	notSupported();

	  	return( null );
	  }

	  @Override
	  public boolean
	  getUnsafeBooleanParameter(
			  String		key,
			  boolean		default_value )
	  {
		  notSupported();

		  return( false );
	  }

	  @Override
	  public void
	  setUnsafeBooleanParameter(
			  String		key,
			  boolean		value )
	  {
		  notSupported();
	  }

	  @Override
	  public int
	  getUnsafeIntParameter(
			  String		key,
			  int		default_value )
	  {
		  notSupported();

		  return( 0 );
	  }

	  @Override
	  public void
	  setUnsafeIntParameter(
			  String		key,
			  int		value )
	  {
		  notSupported();
	  }

	  @Override
	  public long
	  getUnsafeLongParameter(
			  String		key,
			  long		default_value )
	  {
		  notSupported();

		  return( 0 );
	  }

	  @Override
	  public void
	  setUnsafeLongParameter(
			  String		key,
			  long		value )
	  {
		  notSupported();
	  }

	  @Override
	  public float
	  getUnsafeFloatParameter(
			  String		key,
			  float		default_value )
	  {
		  notSupported();

		  return( 0 );
	  }

	  @Override
	  public void
	  setUnsafeFloatParameter(
			  String		key,
			  float		value )
	  {
		  notSupported();
	  }

	  @Override
	  public String
	  getUnsafeStringParameter(
			  String		key,
			  String		default_value )
	  {
		  notSupported();

		  return( null );
	  }

	  @Override
	  public void
	  setUnsafeStringParameter(
			  String		key,
			  String		value )
	  {
		  notSupported();
	  }

	  @Override
	  public Map
	  getUnsafeParameterList()
	  {
		  notSupported();

		  return( null );
	  }

	  @Override
	  public void
	  save()
	  	throws PluginException
	  {
	  	try{
	  		_dispatcher.dispatch( new RPRequest( this, "save", null)).getResponse();

		}catch( RPException e ){

			Throwable cause = e.getCause();

			if ( cause instanceof PluginException ){

				throw((PluginException)cause);
			}

			throw( e );
		}
	  }

		@Override
		public File
		getPluginUserFile(
			String	name )
		{
			notSupported();

			return( null );
		}

		@Override
		public void
		addListener(
			final PluginConfigListener l )
		{
			notSupported();
		}

		@Override
		public void removeListener(PluginConfigListener l) {
			notSupported();
		}

	// @see com.biglybt.pif.PluginConfig#setPluginConfigKeyPrefix(java.lang.String)

		@Override
		public void setPluginConfigKeyPrefix(String _key) {
			// TODO Auto-generated method stub

		}

		@Override
		public boolean hasParameter(String x) {notSupported(); return false;}
		@Override
		public boolean hasPluginParameter(String x) {notSupported(); return false;}
		@Override
		public boolean removePluginParameter(String x) {notSupported(); return false;}
		@Override
		public boolean removePluginColorParameter(String x) {notSupported(); return false;}

	@Override
		  public byte[] getPluginByteParameter(String key) {notSupported(); return null;}
		  @Override
		  public float getPluginFloatParameter(String key) {notSupported(); return 0;}
		  @Override
		  public float getPluginFloatParameter(String key, float default_value) {notSupported(); return 0;}
		  @Override
		  public long getPluginLongParameter(String key) {notSupported(); return 0;}
		  @Override
		  public long getPluginLongParameter(String key, long default_value) {notSupported(); return 0;}
		  @Override
		  public void setPluginParameter(String key, float value) {notSupported();}
		  @Override
		  public void setPluginParameter(String key, long value) {notSupported();}
		  @Override
		  public boolean getUnsafeBooleanParameter(String key) {notSupported(); return false;}
		  @Override
		  public byte[] getUnsafeByteParameter(String key) {notSupported(); return null;}
		  @Override
		  public byte[] getUnsafeByteParameter(String key, byte[] default_value) {notSupported(); return null;}
		  @Override
		  public float getUnsafeFloatParameter(String key) {notSupported(); return 0;}
		  @Override
		  public int getUnsafeIntParameter(String key) {notSupported(); return 0;}
		  @Override
		  public long getUnsafeLongParameter(String key) {notSupported(); return 0;}
		  @Override
		  public String getUnsafeStringParameter(String key) {notSupported(); return null;}
		  @Override
		  public void setUnsafeByteParameter(String key, byte[] value) {notSupported();}

		  @Override
		  public boolean getCoreBooleanParameter(String key) {notSupported(); return false;}
		  @Override
		  public byte[] getCoreByteParameter(String key) {notSupported(); return null;}
		  @Override
		  public float getCoreFloatParameter(String key) {notSupported(); return 0;}
		  @Override
		  public int getCoreIntParameter(String key) {notSupported(); return 0;}
		  @Override
		  public String getCoreStringParameter(String key) {notSupported(); return null;}
		  @Override
		  public long getCoreLongParameter(String key) {notSupported(); return 0;}
		  @Override
		  public void setCoreBooleanParameter(String key, boolean value) {notSupported();}
		  @Override
		  public void setCoreByteParameter(String key, byte[] value) {notSupported();}
		  @Override
		  public void setCoreFloatParameter(String key, float value) {notSupported();}
		  @Override
		  public void setCoreIntParameter(String key, int value) {notSupported();}
		  @Override
		  public void setCoreLongParameter(String key, long value) {notSupported();}
		  @Override
		  public void setCoreStringParameter(String key, String value) {notSupported();}

		  @Override
		  public int[] getCoreColorParameter(String key) {notSupported(); return null;}
		  @Override
		  public void setCoreColorParameter(String key, int[] value) {notSupported();}
		  @Override
		  public void setCoreColorParameter(String key, int[] value, boolean override) {notSupported();}
		  @Override
		  public int[] getPluginColorParameter(String key) {notSupported(); return null;}
		  @Override
		  public int[] getPluginColorParameter(String key, int[] default_value) {notSupported(); return null;}
		  @Override
		  public void setPluginColorParameter(String key, int[] value) {notSupported();}
		  @Override
		  public void setPluginColorParameter(String key, int[] value, boolean override) {notSupported();}
		  @Override
		  public int[] getUnsafeColorParameter(String key) {notSupported(); return null;}
		  @Override
		  public int[] getUnsafeColorParameter(String key, int[] default_value) {notSupported(); return null;}
		  @Override
		  public void setUnsafeColorParameter(String key, int[] default_value) {notSupported();}
		  @Override
		  public void setUnsafeColorParameter(String key, int[] default_value, boolean override) {notSupported();}
		  @Override
		  public PluginConfigSource getPluginConfigSource() {notSupported(); return null;}
		  @Override
		  public void setPluginConfigSource(PluginConfigSource source) {notSupported();}
		  @Override
		  public PluginConfigSource enableExternalConfigSource() {notSupported(); return null;}

		  @Override
		  public void setPluginStringListParameter(String key, String[] value)  {notSupported();}
		  @Override
		  public String[] getPluginStringListParameter(String key)  {notSupported(); return null;}
}
