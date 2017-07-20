/*
 * Created on 28-Apr-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.pifimpl.local.ui.model;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.Properties;

import com.biglybt.core.internat.MessageText;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.ui.components.UITextArea;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pifimpl.local.PluginConfigImpl;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.pifimpl.local.ui.config.FileParameter;

public class
BasicPluginConfigModelImpl
	implements BasicPluginConfigModel
{
	private UIManagerImpl		ui_manager;

	private String					parent_section;
	private String					section;
	private PluginInterface			pi;
	private ArrayList<Parameter>	parameters = new ArrayList<>();

	private String				key_prefix;

	private PluginConfigImpl    configobj;

	public
	BasicPluginConfigModelImpl(
		UIManagerImpl		_ui_manager,
		String				_parent_section,
		String				_section )
	{
		ui_manager		= _ui_manager;
		parent_section	= _parent_section;
		section			= _section;

		pi				= ui_manager.getPluginInterface();

		key_prefix		= pi.getPluginconfig().getPluginConfigKeyPrefix();
		configobj       = (PluginConfigImpl)pi.getPluginconfig();

		if ( parent_section != null && !parent_section.equals( ConfigSection.SECTION_ROOT )){

			String version = pi.getPluginVersion();

			addLabelParameter2( "!" + MessageText.getString( "ConfigView.pluginlist.column.version" ) + ": " + (version==null?"<local>":version) + "!" );
		}
	}

	@Override
	public String
	getParentSection()
	{
		return( parent_section );
	}

	@Override
	public String
	getSection()
	{
		return( section );
	}

	@Override
	public PluginInterface
	getPluginInterface()
	{
		return( pi );
	}

	@Override
	public Parameter[]
	getParameters()
	{
		Parameter[] res = new Parameter[parameters.size()];

		parameters.toArray( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.BooleanParameter
	addBooleanParameter2(
		String 		key,
		String 		resource_name,
		boolean 	defaultValue )
	{
		BooleanParameterImpl res = new BooleanParameterImpl(configobj, resolveKey(key), resource_name, defaultValue );

		parameters.add( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.StringParameter
	addStringParameter2(
		String 		key,
		String 		resource_name,
		String  	defaultValue )
	{
		StringParameterImpl res = new StringParameterImpl(configobj, resolveKey(key), resource_name, defaultValue );

		parameters.add( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.StringListParameter
	addStringListParameter2(
		String 		key,
		String 		resource_name,
		String[]	values,
		String	 	defaultValue )
	{
		StringListParameterImpl res = new StringListParameterImpl(configobj, resolveKey(key), resource_name, defaultValue, values, values );

		parameters.add( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.StringListParameter
	addStringListParameter2(
		String 		key,
		String 		resource_name,
		String[]	values,
		String[]	labels,
		String	 	defaultValue )
	{
		StringListParameterImpl res = new StringListParameterImpl(configobj,
				resolveKey(key), resource_name, defaultValue,
				values, labels);

		parameters.add(res);

		return (res);
	}

	@Override
	public com.biglybt.pif.ui.config.PasswordParameter
	addPasswordParameter2(
		String 		key,
		String 		resource_name,
		int			encoding_type,
		byte[]	 	defaultValue )
	{
		PasswordParameterImpl res = new PasswordParameterImpl(configobj, resolveKey(key), resource_name, encoding_type, defaultValue );

		parameters.add( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.IntParameter
	addIntParameter2(
		String 		key,
		String 		resource_name,
		int	 		defaultValue )
	{
		IntParameterImpl res = new IntParameterImpl(configobj, resolveKey(key), resource_name, defaultValue );

		parameters.add( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.IntParameter
	addIntParameter2(
		String 		key,
		String 		resource_name,
		int	 		defaultValue,
		int         min_value,
		int         max_value)
	{
		IntParameterImpl res = new IntParameterImpl(configobj, resolveKey(key), resource_name, defaultValue, min_value, max_value );
		parameters.add( res );
		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.DirectoryParameter
	addDirectoryParameter2(
		String 		key,
		String 		resource_name,
		String 		defaultValue )
	{
		DirectoryParameterImpl res = new DirectoryParameterImpl(configobj, resolveKey(key), resource_name, defaultValue );

		parameters.add( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.FileParameter
	addFileParameter2(
			String 		key,
			String 		resource_name,
			String 		defaultValue ) {
		return addFileParameter2(key, resource_name, defaultValue, null);
	}

	@Override
	public com.biglybt.pif.ui.config.FileParameter
	addFileParameter2(
			String 		key,
			String 		resource_name,
			String 		defaultValue,
		    String[]    file_extensions) {
		FileParameter res = new FileParameter(configobj, resolveKey(key), resource_name, defaultValue, file_extensions);
		parameters.add(res);
		return res;
	}


	@Override
	public LabelParameter
	addLabelParameter2(
		String		resource_name )
	{
		LabelParameterImpl res = new LabelParameterImpl(configobj, key_prefix, resource_name );

		parameters.add( res );

		return( res );
	}

	@Override
	public InfoParameter
	addInfoParameter2(
		String		resource_name,
		String		value )
	{
		InfoParameterImpl res = new InfoParameterImpl(configobj, resolveKey(resource_name), resource_name, value );

		parameters.add( res );

		return( res );
	}

	@Override
	public com.biglybt.pif.ui.config.HyperlinkParameter
	addHyperlinkParameter2(String resource_name, String url_location) {
		HyperlinkParameterImpl res = new HyperlinkParameterImpl(configobj, key_prefix, resource_name, url_location);
		parameters.add(res);
		return res;
	}

	@Override
	public com.biglybt.pif.ui.config.ColorParameter
	addColorParameter2(String key, String resource_name, int r, int g, int b) {
		ColorParameterImpl res = new ColorParameterImpl(configobj, resolveKey(key), resource_name, r, g, b);
		parameters.add(res);
		return res;
	}

	@Override
	public com.biglybt.pif.ui.config.UIParameter
	addUIParameter2(com.biglybt.pif.ui.config.UIParameterContext context, String resource_name) {
		UIParameterImpl res = new UIParameterImpl(configobj, context, key_prefix, resource_name);
		parameters.add(res);
		return res;
	}

	@Override
	public ActionParameter
	addActionParameter2(
		String 		label_resource_name,
		String		action_resource_name )
	{
		ActionParameterImpl res = new ActionParameterImpl(configobj, label_resource_name, action_resource_name );

		parameters.add( res );

		return( res );
	}

	@Override
	public UITextArea
	addTextArea(
		String		resource_name )
	{
		UITextAreaImpl res = new UITextAreaImpl( configobj, resource_name );

		parameters.add( res );

		return( res );
	}

	@Override
	public ParameterGroup
	createGroup(
		String											_resource_name,
		com.biglybt.pif.ui.config.Parameter[]	_parameters )
	{
		ParameterGroupImpl	pg = new ParameterGroupImpl( _resource_name, _parameters );

		return( pg );
	}

	@Override
	public ParameterTabFolder
	createTabFolder()
	{
		return( new ParameterTabFolderImpl());
	}

	@Override
	public void
	destroy()
	{
		ui_manager.destroy( this );

		for (int i=0;i<parameters.size();i++){

			((ParameterImpl)parameters.get(i)).destroy();
		}
	}

	@Override
	public void setLocalizedName(String name) {
		Properties props = new Properties();
		props.put("ConfigView.section." + this.section, name);
		this.pi.getUtilities().getLocaleUtilities().integrateLocalisedMessageBundle(props);
	}

	protected String
	resolveKey(
		String	key )
	{
		if ( key.startsWith("!") && key.endsWith( "!" )){

			return( key.substring(1, key.length()-1 ));
		}

		return( key_prefix + key );
	}
}
