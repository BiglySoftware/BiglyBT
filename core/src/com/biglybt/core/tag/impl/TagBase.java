/*
 * Created on Mar 20, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */


package com.biglybt.core.tag.impl;

import java.io.File;
import java.util.*;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateFactory;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureExecOnAssign.OptionsTemplateHandler;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.tag.TagFeatureProperties.TagPropertyListener;
import com.biglybt.core.util.*;
import com.biglybt.core.util.DataSourceResolver.DataSourceImporter;
import com.biglybt.core.util.DataSourceResolver.ExportedDataSource;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pif.utils.StaticUtilities;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.util.MapUtils;

public abstract class
TagBase
	implements Tag, SimpleTimer.TimerTickReceiver, DataSourceResolver.ExportableDataSource
{
	protected static final String	AT_RATELIMIT_UP					= "rl.up";
	protected static final String	AT_RATELIMIT_DOWN				= "rl.down";
	protected static final String	AT_VISIBLE						= "vis";
	protected static final String	AT_PUBLIC						= "pub";
	protected static final String	AT_FLAGS						= "flag";
	protected static final String	AT_GROUP						= "gr";
	protected static final String	AT_CAN_BE_PUBLIC				= "canpub";
	protected static final String	AT_ORIGINAL_NAME				= "oname";
	protected static final String	AT_IMAGE_ID						= "img.id";
	protected static final String	AT_IMAGE_FILE					= "img.file";
	protected static final String	AT_IMAGE_SORT_ORDER				= "img.so";
	protected static final String	AT_COLOR_ID						= "col.rgb";
	protected static final String	AT_COLORS_ID					= "cols";
	protected static final String	AT_RSS_ENABLE					= "rss.enable";
	protected static final String	AT_RATELIMIT_UP_PRI				= "rl.uppri";
	protected static final String	AT_XCODE_TARGET					= "xcode.to";
	
	protected static final String	AT_FL_PREVENT_DELETE			= "fl.pd";
	protected static final String	AT_FL_MOVE_COMP					= "fl.comp";
	protected static final String	AT_FL_MOVE_COMP_OPT				= "fl.comp.o";
	protected static final String	AT_FL_MOVE_REM					= "fl.rem";
	protected static final String	AT_FL_MOVE_REM_OPT				= "fl.rem.o";
	protected static final String	AT_FL_COPY_COMP					= "fl.copy";
	protected static final String	AT_FL_COPY_COMP_OPT				= "fl.copy.o";
	protected static final String	AT_FL_INIT_LOC					= "fl.init";
	protected static final String	AT_FL_INIT_LOC_OPT				= "fl.init.o";
	protected static final String	AT_FL_MOVE_ASSIGN				= "fl.ass";
	protected static final String	AT_FL_MOVE_ASSIGN_OPT			= "fl.ass.o";

	protected static final String	AT_RATELIMIT_MIN_SR						= "rl.minsr";
	protected static final String	AT_RATELIMIT_MAX_SR						= "rl.maxsr";
	protected static final String	AT_RATELIMIT_MAX_SR_ACTION				= "rl.maxsr.a";
	protected static final String	AT_RATELIMIT_MAX_AGGREGATE_SR			= "rl.maxaggsr";
	protected static final String	AT_RATELIMIT_MAX_AGGREGATE_SR_ACTION	= "rl.maxaggsr.a";
	protected static final String	AT_RATELIMIT_MAX_AGGREGATE_SR_PRIORITY	= "rl.maxaggsr.p";
	protected static final String	AT_RATELIMIT_FP_SEEDING					= "rl.fps";
	protected static final String	AT_RATELIMIT_BOOST						= "rl.bst";
	protected static final String	AT_RATELIMIT_MAX_ACTIVE_DL				= "rl.maxadl";
	protected static final String	AT_RATELIMIT_MAX_ACTIVE_CD				= "rl.maxacd";
	
	protected static final String	AT_PROPERTY_PREFIX				= "pp.";
	//protected static final String	AT_EOA_PREFIX					= "eoa.";	// meh, should be used but copy/paste error resulted in AT_PROPERTY_PREFIX being used instead 
	protected static final String	AT_BYTES_UP						= "b.up";
	protected static final String	AT_BYTES_DOWN					= "b.down";
	protected static final String	AT_DESCRIPTION					= "desc";
	protected static final String	AT_MAX_TAGGABLES				= "max.t";
	protected static final String	AT_REMOVAL_STRATEGY				= "max.t.r";
	protected static final String	AT_EOS_SCRIPT					= "eos.scr";
	protected static final String	AT_EOS_OPTIONS_TEMPLATE			= "eos.ot";
	protected static final String	AT_EOS_PM						= "eos.pm";
	protected static final String	AT_NOTIFICATION_POST			= "noti.post";
	protected static final String	AT_NOTIFICATION_PUBLISH			= "noti.pub";
	protected static final String	AT_LIMIT_ORDERING				= "max.t.o";
	protected static final String	AT_EOS_ASSIGN_TAGS				= "eos.at";
	protected static final String	AT_EOS_REMOVE_TAGS				= "eos.rt";

	private static final String[] EMPTY_STRING_LIST = {};

	final TagTypeBase	tag_type;

	private final int			tag_id;
	private String		tag_name;

	private static final int TL_ADD 	= 1;
	private static final int TL_REMOVE 	= 2;
	private static final int TL_SYNC 	= 3;

	private final ListenerManager<TagListener>	t_listeners 	=
		ListenerManager.createManager(
			"TagListeners",
			new ListenerManagerDispatcher<TagListener>()
			{
				@Override
				public void
				dispatch(
					TagListener			listener,
					int					type,
					Object				value )
				{
					if ( type == TL_ADD ){

						listener.taggableAdded(TagBase.this,(Taggable)value);

					}else if ( type == TL_REMOVE ){

						listener.taggableRemoved(TagBase.this,(Taggable)value);

					}else if ( type == TL_SYNC ){

						listener.taggableSync( TagBase.this );
					}
				}
			});

	private final Map<com.biglybt.pif.tag.TagListener, TagListener>	listener_map = new HashMap<>();

	private Boolean	is_visible;
	private Boolean	is_public;
	private long	flags;
	private String	group;
	private int[]	colour;
	private long[]	colours;
	private String	description;

	private int		image_sort_order = Integer.MIN_VALUE;

	private TagFeatureRateLimit		tag_rl;
	private TagFeatureRSSFeed		tag_rss;
	private TagFeatureFileLocation	tag_fl;
	private TagFeatureLimits		tag_limits;

	private HashMap<String,Object>		transient_properties;
	
	protected
	TagBase(
		TagTypeBase			_tag_type,
		int					_tag_id,
		String				_tag_name )
	{
		tag_type		= _tag_type;
		tag_id			= _tag_id;
		tag_name		= _tag_name;

		if ( getManager().isEnabled()){

			is_visible 	= readBooleanAttribute( AT_VISIBLE, null );
			is_public 	= readBooleanAttribute( AT_PUBLIC, null );
			flags		= readLongAttribute( AT_FLAGS, FL_NONE );
			group		= readStringAttribute( AT_GROUP, null );
			description = readStringAttribute( AT_DESCRIPTION, null );

			if ( this instanceof TagFeatureRateLimit ){

				tag_rl = (TagFeatureRateLimit)this;
			}

			if ( this instanceof TagFeatureRSSFeed ){

				tag_rss = (TagFeatureRSSFeed)this;

				if ( tag_rss.isTagRSSFeedEnabled()){

					getManager().checkRSSFeeds( this, true );
				}
			}

			if ( this instanceof TagFeatureFileLocation ){

				tag_fl = (TagFeatureFileLocation)this;
			}

			if ( this instanceof TagFeatureLimits ){

				tag_limits = (TagFeatureLimits)this;
			}
		}
	}

	protected void
	initialized()
	{
			// need to defer group setting until this point as the tag-group can have listeners and we 
			// don't want the Tag to 'escape' to the world before it is initialized
		
		if ( group != null ){
			
			tag_type.setTagGroup( this, null, group );
		}
		
		loadPersistentStuff();

		loadTransientStuff();
	}

	@Override
	public ExportedDataSource 
	exportDataSource()
	{
		return(
			new ExportedDataSource()
			{
				public Class<? extends DataSourceImporter>
				getExporter()
				{
					return( TagManagerImpl.class );
				}
				
				public Map<String,Object>
				getExport()
				{
					Map	m = new HashMap<String,Object>();
					
					m.put( "uid", getTagUID());
					
					return( m );
				}
			});
	}
	
	public Tag
	getTag()
	{
		return( this );
	}

	protected void
	addTag()
	{
		if ( getManager().isEnabled()){

			tag_type.addTag( this );
		}
	}

	protected TagManagerImpl
	getManager()
	{
		return( tag_type.getTagManager());
	}

	@Override
	public TagTypeBase
	getTagType()
	{
		return( tag_type );
	}

	@Override
	public int
	getTagID()
	{
		return( tag_id );
	}

	@Override
	public long
	getTagUID()
	{
		return((((long)getTagType().getTagType())<<32) | tag_id );
	}

	@Override
	public String
	getTagName()
	{
		return( getTagName( true ));
	}

	protected String
	getTagNameRaw()
	{
		return( tag_name );
	}

	@Override
	public String
	getTagName(
		boolean		localize )
	{
		if ( localize ){

			if ( tag_name.startsWith( "tag." )){

				return( MessageText.getString( tag_name ));

			}else{

				return( tag_name );
			}
		}else{

			if ( tag_name.startsWith( "tag." )){

				return( tag_name );

			}else{

				String original_name = readStringAttribute( AT_ORIGINAL_NAME, null );

				if ( original_name != null && original_name.startsWith( "tag." )){

					return( original_name );
				}

				return( "!" + tag_name + "!" );
			}
		}
	}

	@Override
	public void
	setTagName(
		String name )

		throws TagException
	{
		if ( getTagType().isTagTypeAuto()){

			throw( new TagException( "Not supported" ));
		}

		if ( tag_name.startsWith( "tag." )){

			String original_name = readStringAttribute( AT_ORIGINAL_NAME, null );

			if ( original_name == null ){

				writeStringAttribute( AT_ORIGINAL_NAME, tag_name );
			}
		}

		tag_name = name;

		tag_type.fireMetadataChanged( this );
	}

		// public

	@Override
	public boolean
	isPublic()
	{
		boolean pub = is_public==null?getPublicDefault():is_public;

		if ( pub ){

			boolean[] autos = isTagAuto();

			if ( autos[0] || autos[1] ){

				pub = false;
			}
		}

		return( pub );
	}

	@Override
	public void
	setPublic(
		boolean	v )
	{
		if ( is_public == null || v != is_public ){

			if ( v && !canBePublic()){

				Debug.out( "Invalid attempt to set public" );

				return;
			}

			is_public	= v;

			writeBooleanAttribute( AT_PUBLIC, v );

			tag_type.fireMetadataChanged( this );
		}
	}

	protected boolean
	getPublicDefault()
	{
		if ( !getCanBePublicDefault()){

			return( false );
		}

		return( tag_type.getTagManager().getTagPublicDefault());
	}

	@Override
	public void
	setCanBePublic(
		boolean	can_be_public )
	{
		writeBooleanAttribute( AT_CAN_BE_PUBLIC, can_be_public );

		if ( !can_be_public ){

			if ( isPublic()){

				setPublic( false );
			}
		}
	}

	@Override
	public boolean
	canBePublic()
	{
		boolean result = readBooleanAttribute( AT_CAN_BE_PUBLIC, getCanBePublicDefault());
		
		if ( result ){
			
			boolean[] autos = isTagAuto();

			if ( autos[0] || autos[1] ){

				result = false;
			}
		}
		
		return( result );
	}

	protected boolean
	getCanBePublicDefault()
	{
		return( true );
	}

	public void
	setFlag(
		long		flag,
		boolean		value )
	{
		boolean set = ( flags & flag ) != 0;
		
		if ( set == value ){
			
			return;
		}
		
		if ( value ){
			
			flags = flags | flag;
			
		}else{
			
			flags = flags & ~flag;
		}
		
		writeLongAttribute( AT_FLAGS, flags );

		tag_type.fireMetadataChanged( this );
	}
	
	public boolean
	getFlag(
		long		flag )
	{
		return((flags & flag) != 0 );
	}
	
	@Override
	public boolean[]
	isTagAuto()
	{
		return( new boolean[]{ false, false, false });
	}

		// visible

	@Override
	public boolean
	isVisible()
	{
		return( is_visible==null?getVisibleDefault():is_visible );
	}

	@Override
	public void
	setVisible(
		boolean	v )
	{
		if ( is_visible == null || v != is_visible ){

			is_visible	= v;

			writeBooleanAttribute( AT_VISIBLE, v );

			tag_type.fireMetadataChanged( this );
		}
	}

	@Override
	public String
	getGroup()
	{
		return( group );
	}

	@Override
	public void
	setGroup(
		String		new_group )
	{
		if ( group == null && new_group == null ){

			return;
		}

		if ( group == null || new_group == null || !group.equals(new_group)){

			String	old_name = group;
			
			group	= new_group;

			writeStringAttribute( AT_GROUP, new_group );

			tag_type.setTagGroup( this, old_name, new_group );
			
			tag_type.fireMetadataChanged( this );
		}
	}

	@Override
	public TagGroup 
	getGroupContainer()
	{
		return( tag_type.getTagGroup( group ));
	}
	
	protected boolean
	getVisibleDefault()
	{
		return( true );
	}

	@Override
	public String
	getImageID()
	{
		return( readStringAttribute( AT_IMAGE_ID, null ));
	}

	@Override
	public void
	setImageID(
		String		id )
	{
		writeStringAttribute( AT_IMAGE_ID, id );
		
		tag_type.fireMetadataChanged( this );
	}

	@Override
	public String 
	getImageFile()
	{
		return( readStringAttribute( AT_IMAGE_FILE, null ));
	}
	
	@Override
	public void 
	setImageFile(String id)
	{
		writeStringAttribute( AT_IMAGE_FILE, id );
		
		tag_type.fireMetadataChanged( this );
	}
	
	@Override
	public void
	setImageSortOrder(
		int order)
	{
		image_sort_order = order;
		
		writeLongAttribute( AT_IMAGE_SORT_ORDER, order );
		
		tag_type.fireMetadataChanged( this );
	}
	
	@Override
	public int 
	getImageSortOrder()
	{
		int	result = image_sort_order;
	
		if ( result == Integer.MIN_VALUE ){
		
			result = image_sort_order = readLongAttribute( AT_IMAGE_SORT_ORDER, -1L ).intValue();
		}
		
		return( result );
	}
	
	private int[]
	decodeRGB(
		String str )
	{
		if ( str == null ){

			return( null );
		}

		String[] bits = str.split( "," );

		if ( bits.length != 3 ){

			return( null );
		}

		int[] rgb = new int[3];

		for ( int i=0;i<bits.length;i++){

			try{

				rgb[i] = Integer.parseInt(bits[i]);

			}catch( Throwable e ){

				return( null );
			}
		}

		return( rgb );
	}

	private String
	encodeRGB(
		int[]	rgb )
	{
		if ( rgb == null || rgb.length != 3 ){

			return( null );
		}

		return( rgb[0]+","+rgb[1]+","+rgb[2] );
	}

	public boolean
	isColorDefault()
	{
		return( decodeRGB( readStringAttribute( AT_COLOR_ID, null )) == null );
	}

	@Override
	public int[]
	getColor()
	{
		int[] result = colour;

		if ( result == null ){

			result = decodeRGB( readStringAttribute( AT_COLOR_ID, null ));

			if ( result == null ){

				result = tag_type.getColorDefault();
			}

			colour = result;
		}

		return( result );
	}

	@Override
	public void
	setColor(
		int[]		rgb )
	{
		if ( rgb == null ){
			
			if ( group != null ){
			
				TagGroup tg = getGroupContainer();
				
				if ( tg != null ){
					
					rgb = tg.getColor();
				}
			}
		}
		
		boolean changed = writeStringAttribute( AT_COLOR_ID, encodeRGB( rgb ));

		colour = null;

		if (changed) {
			tag_type.fireMetadataChanged( this );
		}
	}

	@Override
	public long[]
	getColors()
	{
		long[] result = colours;

		if ( result == null ){

			result = readLongListAttribute( AT_COLORS_ID, new long[0] );

			colours = result;
		}

		return( result );
	}

	@Override
	public void
	setColors(
		long[]		params )
	{
		if ( params == null ){
			
			params = new long[0];
		}
		
		boolean	changed;
		
		if ( colours != null && colours.length == 0 && params.length == 0 ){
		
			changed = false;
			
		}else{
		
			changed = writeLongListAttribute( AT_COLORS_ID, params );
			
			if ( colours == null ){
				
				changed = true;
			}
		}
		
		colours = params;

		if ( changed ){
		
			tag_type.fireMetadataChanged( this );
		}
	}
	
	public boolean
	isTagRSSFeedEnabled()
	{
		if ( tag_rss != null ){

			return( readBooleanAttribute( AT_RSS_ENABLE, false ));
		}

		return( false );
	}

	public void
	setTagRSSFeedEnabled(
		boolean		enable )
	{
		if ( tag_rss != null ){

			if ( isTagRSSFeedEnabled() != enable ){

				writeBooleanAttribute( AT_RSS_ENABLE, enable );

				tag_type.fireMetadataChanged( this );

				tag_type.getTagManager().checkRSSFeeds( this, enable );
			}
		}
	}
	
	public boolean
	getPreventDelete()
	{
		if ( tag_fl != null ){
		
			return( readBooleanAttribute( AT_FL_PREVENT_DELETE, false ));
		}
		
		return( false );
	}
	
	public void
	setPreventDelete(
		boolean		b )
	{
		if ( tag_fl != null ){
		
			if ( getPreventDelete() != b ){
			
				writeBooleanAttribute( AT_FL_PREVENT_DELETE, b );
		
				tag_type.fireMetadataChanged( this );
			}
		}
	}

	public boolean
	getTagBoost()
	{
		if ( tag_rl != null ){
			
			return( readBooleanAttribute( AT_RATELIMIT_BOOST, false ));
		}
		
		return( false );
	}

	public void
	setTagBoost(
		boolean		boost )
	{
		if ( tag_rl != null ){
			
			if ( getTagBoost() != boost ){
			
				writeBooleanAttribute( AT_RATELIMIT_BOOST, boost );
		
				tag_type.fireMetadataChanged( this );
			}
		}
	}
	
	public int
	getMaxActiveDownloads()
	{
		if ( tag_rl != null ){
			
			return( readLongAttribute( AT_RATELIMIT_MAX_ACTIVE_DL, 0L ).intValue());
		}
		
		return( 0 );
	}

	public void
	setMaxActiveDownloads(
		int			max )
	{
		if ( tag_rl != null ){
			
			if ( getMaxActiveDownloads() != max ){
			
				writeLongAttribute( AT_RATELIMIT_MAX_ACTIVE_DL, max );
		
				tag_type.fireMetadataChanged( this );
			}
		}
	}
	
	public int
	getMaxActiveSeeds()
	{
		if ( tag_rl != null ){
			
			return( readLongAttribute( AT_RATELIMIT_MAX_ACTIVE_CD, 0L ).intValue());
		}
		
		return( 0 );
	}

	public void
	setMaxActiveSeeds(
		int			max )
	{
		if ( tag_rl != null ){
			
			if ( getMaxActiveSeeds() != max ){
			
				writeLongAttribute( AT_RATELIMIT_MAX_ACTIVE_CD, max );
		
				tag_type.fireMetadataChanged( this );
			}
		}
	}
	
		// initial save location

	public boolean
	supportsTagInitialSaveFolder()
	{
		return( false );
	}

	public File
	getTagInitialSaveFolder()
	{
		if ( tag_fl != null ){

			String str = readStringAttribute( AT_FL_INIT_LOC, null );

			if ( str == null ){

				return( null );

			}else{

				return( FileUtil.newFile( str ));
			}
		}

		return( null );
	}

	public void
	setTagInitialSaveFolder(
		File		folder )
	{
		if ( tag_fl != null ){

			File	existing = getTagInitialSaveFolder();

			if ( existing == null && folder == null ){

				return;

			}else if ( existing == null || folder == null || !existing.equals( folder )){

				boolean changed = writeStringAttribute( AT_FL_INIT_LOC, folder==null?null:folder.getAbsolutePath());

				if (changed) {
					tag_type.fireMetadataChanged( this );
				}
			}
		}
	}

	public long
	getTagInitialSaveOptions()
	{
		if ( tag_fl != null ){

			return( readLongAttribute( AT_FL_INIT_LOC_OPT, TagFeatureFileLocation.FL_DEFAULT ));
		}

		return( TagFeatureFileLocation.FL_NONE );
	}

	public void
	setTagInitialSaveOptions(
		long		options )
	{
		if ( tag_fl != null ){

			long	existing = getTagInitialSaveOptions();

			if ( existing != options ){

				writeLongAttribute( AT_FL_INIT_LOC_OPT, options );

				tag_type.fireMetadataChanged( this );
			}
		}
	}

		// move on complete

	public boolean
	supportsTagMoveOnComplete()
	{
		return( false );
	}

	public File
	getTagMoveOnCompleteFolder()
	{
		if ( tag_fl != null ){

			String str = readStringAttribute( AT_FL_MOVE_COMP, null );

			if ( str == null ){

				return( null );

			}else{

				return( FileUtil.newFile( str ));
			}
		}

		return( null );
	}

	public void
	setTagMoveOnCompleteFolder(
		File		folder )
	{
		if ( tag_fl != null ){

			File	existing = getTagMoveOnCompleteFolder();

			if ( existing == null && folder == null ){

				return;

			}else if ( existing == null || folder == null || !existing.equals( folder )){

				boolean changed = writeStringAttribute( AT_FL_MOVE_COMP, folder==null?null:folder.getAbsolutePath());

				if (changed) {
					tag_type.fireMetadataChanged( this );
				}
			}
		}
	}

	public long
	getTagMoveOnCompleteOptions()
	{
		if ( tag_fl != null ){

			return( readLongAttribute( AT_FL_MOVE_COMP_OPT, TagFeatureFileLocation.FL_DEFAULT ));
		}

		return( TagFeatureFileLocation.FL_NONE );
	}

	public void
	setTagMoveOnCompleteOptions(
		long		options )
	{
		if ( tag_fl != null ){

			long	existing = getTagMoveOnCompleteOptions();

			if ( existing != options ){

				writeLongAttribute( AT_FL_MOVE_COMP_OPT, options );

				tag_type.fireMetadataChanged( this );
			}
		}
	}

		// copy on complete

	public boolean
	supportsTagCopyOnComplete()
	{
		return( false );
	}

	public File
	getTagCopyOnCompleteFolder()
	{
		if ( tag_fl != null ){

			String str = readStringAttribute( AT_FL_COPY_COMP, null );

			if ( str == null ){

				return( null );

			}else{

				return( FileUtil.newFile( str ));
			}
		}

		return( null );
	}

	public void
	setTagCopyOnCompleteFolder(
		File		folder )
	{
		if ( tag_fl != null ){

			File	existing = getTagCopyOnCompleteFolder();

			if ( existing == null && folder == null ){

				return;

			}else if ( existing == null || folder == null || !existing.equals( folder )){

				boolean changed = writeStringAttribute( AT_FL_COPY_COMP, folder==null?null:folder.getAbsolutePath());

				if (changed) {
					tag_type.fireMetadataChanged( this );
				}
			}
		}
	}

	public long
	getTagCopyOnCompleteOptions()
	{
		if ( tag_fl != null ){

			return( readLongAttribute( AT_FL_COPY_COMP_OPT, TagFeatureFileLocation.FL_DEFAULT ));
		}

		return( TagFeatureFileLocation.FL_NONE );
	}

	public void
	setTagCopyOnCompleteOptions(
		long		options )
	{
		if ( tag_fl != null ){

			long	existing = getTagCopyOnCompleteOptions();

			if ( existing != options ){

				writeLongAttribute( AT_FL_COPY_COMP_OPT, options );

				tag_type.fireMetadataChanged( this );
			}
		}
	}
	
		// move on remove

	public boolean
	supportsTagMoveOnRemove()
	{
		return( false );
	}
	
	public File
	getTagMoveOnRemoveFolder()
	{
		if ( tag_fl != null ){
	
			String str = readStringAttribute( AT_FL_MOVE_REM, null );
	
			if ( str == null ){
	
				return( null );
	
			}else{
	
				return( FileUtil.newFile( str ));
			}
		}
	
		return( null );
	}
	
	public void
	setTagMoveOnRemoveFolder(
		File		folder )
	{
		if ( tag_fl != null ){
	
			File	existing = getTagMoveOnRemoveFolder();
	
			if ( existing == null && folder == null ){
	
				return;
	
			}else if ( existing == null || folder == null || !existing.equals( folder )){
	
				boolean changed = writeStringAttribute( AT_FL_MOVE_REM, folder==null?null:folder.getAbsolutePath());
	
				if (changed) {
					tag_type.fireMetadataChanged( this );
				}
			}
		}
	}
	
	public long
	getTagMoveOnRemoveOptions()
	{
		if ( tag_fl != null ){
	
			return( readLongAttribute( AT_FL_MOVE_REM_OPT, TagFeatureFileLocation.FL_DEFAULT ));
		}
	
		return( TagFeatureFileLocation.FL_NONE );
	}
	
	public void
	setTagMoveOnRemoveOptions(
		long		options )
	{
		if ( tag_fl != null ){
	
			long	existing = getTagMoveOnRemoveOptions();
	
			if ( existing != options ){
	
				writeLongAttribute( AT_FL_MOVE_REM_OPT, options );
	
				tag_type.fireMetadataChanged( this );
			}
		}
	}

		// move on assign
	
	public boolean
	supportsTagMoveOnAssign()
	{
		return( false );
	}
	
	public File
	getTagMoveOnAssignFolder()
	{
		if ( tag_fl != null ){
	
			String str = readStringAttribute( AT_FL_MOVE_ASSIGN, null );
	
			if ( str == null ){
	
				return( null );
	
			}else{
	
				return( FileUtil.newFile( str ));
			}
		}
	
		return( null );
	}
	
	public void
	setTagMoveOnAssignFolder(
		File		folder )
	{
		if ( tag_fl != null ){
	
			File	existing = getTagMoveOnAssignFolder();
	
			if ( existing == null && folder == null ){
	
				return;
	
			}else if ( existing == null || folder == null || !existing.equals( folder )){
	
				boolean changed = writeStringAttribute( AT_FL_MOVE_ASSIGN, folder==null?null:folder.getAbsolutePath());
	
				if ( changed ){
	
						// update initial-save-location too as this is what drives
						// any initial locations - the move-on-assign code is explicitly implemented to only apply 
						// post-initial-location handling
						
					setTagInitialSaveFolder( folder );
					
					tag_type.fireMetadataChanged( this );
				}
			}
		}
	}
	
	public long
	getTagMoveOnAssignOptions()
	{
		if ( tag_fl != null ){
	
			return( readLongAttribute( AT_FL_MOVE_ASSIGN_OPT, TagFeatureFileLocation.FL_DEFAULT ));
		}
	
		return( TagFeatureFileLocation.FL_NONE );
	}
	
	public void
	setTagMoveOnAssignOptions(
		long		options )
	{
		if ( tag_fl != null ){
	
			long	existing = getTagMoveOnAssignOptions();
	
			if ( existing != options ){
	
				writeLongAttribute( AT_FL_MOVE_ASSIGN_OPT, options );
	
				tag_type.fireMetadataChanged( this );
			}
		}
	}


		// min ratio

	public int
	getTagMinShareRatio()
	{
		return( -1 );
	}

	public void
	setTagMinShareRatio(
		int		sr )
	{
		Debug.out( "not supported" );
	}

		// max ratio

	public int
	getTagMaxShareRatio()
	{
		return( -1 );
	}

	public void
	setTagMaxShareRatio(
		int		sr )
	{
		Debug.out( "not supported" );
	}

	public int
	getTagMaxShareRatioAction()
	{
		return( -1 );
	}

	public void
	setTagMaxShareRatioAction(
		int		action )
	{
		Debug.out( "not supported" );
	}

		// aggregate share ratio

	public int
	getTagAggregateShareRatio()
	{
		return( -1 );
	}

	public int
	getTagMaxAggregateShareRatio()
	{
		return( -1 );
	}

	public void
	setTagMaxAggregateShareRatio(
		int		sr )
	{
		Debug.out( "not supported" );
	}

	public int
	getTagMaxAggregateShareRatioAction()
	{
		return( -1 );
	}

	public void
	setTagMaxAggregateShareRatioAction(
		int		action )
	{
		Debug.out( "not supported" );
	}

	public boolean
	getTagMaxAggregateShareRatioHasPriority()
	{
		return( true );
	}

	public void
	setTagMaxAggregateShareRatioHasPriority(
		boolean		priority )
	{
		Debug.out( "not supported" );
	}

	public boolean
	getFirstPrioritySeeding()
	{
		return( true );
	}

	public void
	setFirstPrioritySeeding(
		boolean		priority )
	{
		Debug.out( "not supported" );
	}

	
		// limits

	public int
	getMaximumTaggables()
	{
		if ( tag_limits != null ){

			return( readLongAttribute( AT_MAX_TAGGABLES, 0L ).intValue());
		}

		return( -1 );
	}

	public void
	setMaximumTaggables(
		int		max )
	{
		if ( tag_limits != null ){

			if ( getMaximumTaggables() != max ){

				writeLongAttribute( AT_MAX_TAGGABLES, max );

				tag_type.fireMetadataChanged( this );

				checkMaximumTaggables();
			}
		}
	}

	protected void
	checkMaximumTaggables()
	{
	}

	public int
	getRemovalStrategy()
	{
		if ( tag_limits != null ){

			return( readLongAttribute( AT_REMOVAL_STRATEGY, (long)TagFeatureLimits.RS_DEFAULT ).intValue());
		}

		return( -1 );
	}

	public void
	setRemovalStrategy(
		int		id )
	{
		if ( tag_limits != null ){

			if ( getRemovalStrategy() != id ){

				writeLongAttribute( AT_REMOVAL_STRATEGY, id );

				tag_type.fireMetadataChanged( this );
			}
		}
	}

	public int
	getOrdering()
	{
		if ( tag_limits != null ){

			return( readLongAttribute( AT_LIMIT_ORDERING, (long)TagFeatureLimits.OP_DEFAULT ).intValue());
		}

		return( -1 );
	}

	public void
	setOrdering(
		int		id )
	{
		if ( tag_limits != null ){

			if ( getOrdering() != id ){

				writeLongAttribute( AT_LIMIT_ORDERING, id );

				tag_type.fireMetadataChanged( this );
			}
		}
	}

	public TagProperty[]
	getSupportedProperties()
	{
		return( new TagProperty[0] );
	}

	public TagProperty
	getProperty(
		String		name )
	{
		TagProperty[] props = getSupportedProperties();

		for ( TagProperty prop: props ){

			if ( prop.getName( false ) == name ){

				return( prop );
			}
		}

		return( null );
	}

	protected TagProperty
	createTagProperty(
		String		name,
		int			type )
	{
		return( new TagPropertyImpl( name, type ));
	}

		// exec on assign

	public int
	getSupportedActions()
	{
		return( TagFeatureExecOnAssign.ACTION_NONE );
	}

	public boolean
	supportsAction(
		int		action )
	{
		return((getSupportedActions() & action ) != 0 );
	}

	public boolean
	isAnyActionEnabled()
	{
		for ( int action: TagFeatureExecOnAssign.ACTIONS ){
			
			if ( isActionEnabled(action )){
				
				return( true );
			}
		}
		
		return( false );
	}
	
	public boolean
	isActionEnabled(
		int		action )
	{
		if ( !supportsAction( action )){

			return( false );
		}

		return( readBooleanAttribute( AT_PROPERTY_PREFIX + action, false ));
	}

	public void
	setActionEnabled(
		int			action,
		boolean		enabled )
	{
		if ( !supportsAction( action )){

			if ( enabled ){

				Debug.out( "not supported" );
			}

			return;
		}

		writeBooleanAttribute( AT_PROPERTY_PREFIX + action, enabled );
		
		tag_type.fireMetadataChanged( this );
	}

	public String
	getActionScript()
	{
		String script = readStringAttribute( AT_EOS_SCRIPT, "" );

		if ( script == null ){

			script = "";
		}

		return( script );
	}

	public void
	setActionScript(
		String		script )
	{
		if ( script == null ){

			script = "";
		}

		script = script.trim();

		boolean changed = writeStringAttribute(AT_EOS_SCRIPT, script);

		if (changed) {
			setActionEnabled( TagFeatureExecOnAssign.ACTION_SCRIPT, script.length() > 0 );

			tag_type.fireMetadataChanged( this );
		}
	}

	public String
	getPostMessageChannel()
	{
		String channel = readStringAttribute( AT_EOS_PM, "" );

		if ( channel == null ){

			channel = "";
		}

		return( channel );
	}

	public void
	setPostMessageChannel(
		String		channel )
	{
		if ( channel == null ){

			channel = "";
		}

		channel = channel.trim();

		boolean changed = writeStringAttribute( AT_EOS_PM, channel);

		if (changed) {
			setActionEnabled( TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI, channel.length() > 0 );

			tag_type.fireMetadataChanged( this );
		}
	}
	
	public OptionsTemplateHandler
	getOptionsTemplateHandler()
	{
		return(
			new OptionsTemplateHandler()
			{
				private CopyOnWriteList<ParameterChangeListener>	listeners = new CopyOnWriteList<>();

				
				@Override
				public boolean 
				isActive()
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, null );
					
					return( map != null && !map.isEmpty());
				}
				
				private void
				update(
					Map<String,Object>		map )
				{
					if ( map != null && map.isEmpty()){
						
						map = null;
					}
					
					writeMapAttribute( AT_EOS_OPTIONS_TEMPLATE, map );
					
					if ( map == null ){
						
						setActionEnabled( TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE, false );
						
					}else{
					
						setActionEnabled( TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE, true );
					}
				}
				
				public String
				getName()
				{
					return( MessageText.getString( "label.options.template" ) + " : " + getTagName( true ));
				}
				
				public int
				getUploadRateLimitBytesPerSecond()
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, null );
					
					return( MapUtils.getMapInt(map, "gen_up", 0 ));
				}
				
				public void
				setUploadRateLimitBytesPerSecond(
					int		limit )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, new HashMap<>());
					
					if ( limit == 0 ){
						map.remove( "gen_up" );
					}else{
						map.put( "gen_up", limit );
					}
					
					update( map );
				}
				
				public int
				getDownloadRateLimitBytesPerSecond()
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, null );
					
					return( MapUtils.getMapInt(map, "gen_down", 0 ));
				}
				
				public void
				setDownloadRateLimitBytesPerSecond(
					int		limit )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, new HashMap<>());
					
					if ( limit == 0 ){
						map.remove( "gen_down" );
					}else{
						map.put( "gen_down", limit );
					}
					
					update( map );					
				}

				public int
				getIntParameter(
					String		name )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, null );
					
					return( MapUtils.getMapInt(map, name, DownloadManagerStateFactory.getIntParameterDefault(name) ));
				}
				
				public void
				setIntParameter(
					String		name,
					int			value )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, new HashMap<>());
					
					if ( value == DownloadManagerStateFactory.getIntParameterDefault(name)){
						
						map.remove( name );
						
					}else{
						
						map.put( name, new Long( value ));
					}
					
					update( map );
				}
				
				public boolean
				getBooleanParameter(
					String		name )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, null );
					
					return( MapUtils.getMapBoolean( map, name, DownloadManagerStateFactory.getBooleanParameterDefault(name) ));
				}
				
				public void
				setBooleanParameter(
					String		name,
					boolean		value )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, new HashMap<>());
				
					if ( value == DownloadManagerStateFactory.getBooleanParameterDefault(name)){
						
						map.remove( name );
						
					}else{
						
						map.put( name, value?1:0 );
					}
					
					update( map );
				}
				
				public void
				setParameterDefault(
					String		key )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, null );
					
					if ( map != null ){
						
						map.remove( key );
						
						update( map );
						
						for ( ParameterChangeListener l: listeners ){
							try{
								l.parameterChanged( this );
							}catch( Throwable e ){
								Debug.out( e );
							}
						}
					}
				}
				
				public DownloadManager
				getDownloadManager()
				{
					return( null );
				}
		
				@Override
				public void addListener(ParameterChangeListener listener){
					listeners.add( listener );
				}
				
				@Override
				public void removeListener(ParameterChangeListener listener){
					listeners.remove( listener );
				}
				
				
				@Override
				public void 
				applyTo(
					DownloadManager dm )
				{
					Map<String,Object> map = readMapAttribute( AT_EOS_OPTIONS_TEMPLATE, null );
					
					if ( map == null ){
						
						return;
					}
					
					if ( map.containsKey( "gen_up" )){
						
						int up = MapUtils.getMapInt(map, "gen_up", 0 );
						
						dm.getStats().setUploadRateLimitBytesPerSecond( up );
					}
					
					if ( map.containsKey( "gen_down" )){
						
						int up = MapUtils.getMapInt(map, "gen_down", 0 );
						
						dm.getStats().setDownloadRateLimitBytesPerSecond( up );
					}
					
					DownloadManagerState state = dm.getDownloadState();
					
					for ( String name: map.keySet()){
						
						if ( name.startsWith( "gen_" )){
							
							continue;
						}
						
						if ( DownloadManagerStateFactory.getBooleanParameterDefault( name ) != null ){
							
							state.setBooleanParameter(name, MapUtils.getMapBoolean(map, name, false ));
							
						}else if ( DownloadManagerStateFactory.getIntParameterDefault( name ) != null ){
							
							state.setIntParameter( name, MapUtils.getMapInt(map, name, 0 ));
						}
					}
				}
			});
	}
	
		// notifications

	public int
	getPostingNotifications()
	{
		return( readLongAttribute( AT_NOTIFICATION_POST, (long)TagFeatureNotifications.NOTIFY_NONE ).intValue());
	}

	public void
	setPostingNotifications(
		int		flags )
	{
		writeLongAttribute( AT_NOTIFICATION_POST, flags );
	}
	
	public String
	getNotifyMessageChannel()
	{
		String channel = readStringAttribute( AT_NOTIFICATION_PUBLISH, "" );

		if ( channel == null ){

			channel = "";
		}

		return( channel );
	}

	public void
	setNotifyMessageChannel(
		String		channel )
	{
		if ( channel == null ){

			channel = "";
		}

		channel = channel.trim();

		boolean changed = writeStringAttribute( AT_NOTIFICATION_PUBLISH, channel);

		if (changed) {
	
			tag_type.fireMetadataChanged( this );
		}		
	}
	
		// assign tags
	
	public List<Tag>
	getTagAssigns()
	{
		String[] tag_uids = readStringListAttribute( AT_EOS_ASSIGN_TAGS, new String[0] );

		if ( tag_uids == null || tag_uids.length == 0 ){

			return( Collections.emptyList());
		}
		
		List<Tag> result = new ArrayList<>();

		for ( String uid: tag_uids ){
			
			try{
				Tag tag = tag_type.getTagManager().lookupTagByUID( Long.parseLong( uid ));
				
				if ( tag != null ){
					
					result.add( tag );
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		return( result );
	}
	
	public void
	setTagAssigns(
		List<Tag>	tags )
	{
		if ( tags == null ){

			tags = new ArrayList<>();
		}

		String[] tag_uids = new String[tags.size()];
		
		for ( int i=0;i<tag_uids.length;i++ ){
			
			tag_uids[i] = String.valueOf( tags.get(i).getTagUID());
		}
		
		writeStringListAttribute( AT_EOS_ASSIGN_TAGS, tag_uids);

		setActionEnabled( TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS, !tags.isEmpty());
		
		tag_type.fireMetadataChanged( this );
	}
	
		// remove tags
		
	public List<Tag>
	getTagRemoves()
	{
		String[] tag_uids = readStringListAttribute( AT_EOS_REMOVE_TAGS, new String[0] );
	
		if ( tag_uids == null || tag_uids.length == 0 ){
	
			return( Collections.emptyList());
		}
		
		List<Tag> result = new ArrayList<>();
	
		for ( String uid: tag_uids ){
			
			try{
				Tag tag = tag_type.getTagManager().lookupTagByUID( Long.parseLong( uid ));
				
				if ( tag != null ){
					
					result.add( tag );
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		return( result );
	}
	
	public void
	setTagRemoves(
		List<Tag>	tags )
	{
		if ( tags == null ){
	
			tags = new ArrayList<>();
		}
	
		String[] tag_uids = new String[tags.size()];
		
		for ( int i=0;i<tag_uids.length;i++ ){
			
			tag_uids[i] = String.valueOf( tags.get(i).getTagUID());
		}
		
		writeStringListAttribute( AT_EOS_REMOVE_TAGS, tag_uids);
	
		setActionEnabled( TagFeatureExecOnAssign.ACTION_REMOVE_TAGS, !tags.isEmpty());
		
		tag_type.fireMetadataChanged( this );
	}

	public String
	getPropertiesString()
	{
		TagFeatureProperties.TagProperty[] props = getSupportedProperties();

		String text = "";
		
		if ( props.length > 0 ){

			for ( TagFeatureProperties.TagProperty prop: props ){

				String prop_str = prop.getString();

				if ( prop_str.length() > 0 ){

					text += (text.length()==0?"":"; ") + prop_str;
				}
			}
		}
		
		return( text );
	}
	
	public String
	getEOAString()
	{
		String text = "";
		
		int	actions = getSupportedActions();

		if ( actions != TagFeatureExecOnAssign.ACTION_NONE ){

			String actions_str = "";

			boolean is_peer_set = getTagType().getTagType() == TagType.TT_PEER_IPSET;
			
			int[]	action_ids =
				{	TagFeatureExecOnAssign.ACTION_APPLY_OPTIONS_TEMPLATE,
				 	TagFeatureExecOnAssign.ACTION_DESTROY,
					TagFeatureExecOnAssign.ACTION_START,
					TagFeatureExecOnAssign.ACTION_FORCE_START,
					TagFeatureExecOnAssign.ACTION_NOT_FORCE_START,
					TagFeatureExecOnAssign.ACTION_STOP,
					TagFeatureExecOnAssign.ACTION_QUEUE,
					TagFeatureExecOnAssign.ACTION_SCRIPT,
					TagFeatureExecOnAssign.ACTION_PAUSE,
					TagFeatureExecOnAssign.ACTION_RESUME,
					TagFeatureExecOnAssign.ACTION_POST_MAGNET_URI,
					TagFeatureExecOnAssign.ACTION_MOVE_INIT_SAVE_LOC,
					TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS,
					TagFeatureExecOnAssign.ACTION_REMOVE_TAGS,
					TagFeatureExecOnAssign.ACTION_HOST,
					TagFeatureExecOnAssign.ACTION_PUBLISH };

			String[] action_keys =
				{ 	"label.apply.options.template",
					is_peer_set?"azbuddy.ui.menu.disconnect":"v3.MainWindow.button.delete",
					"v3.MainWindow.button.start",
					"v3.MainWindow.button.forcestart",
					"v3.MainWindow.button.notforcestart",
					"v3.MainWindow.button.stop",
					"ConfigView.section.queue",
					"label.script",
					"v3.MainWindow.button.pause",
					"v3.MainWindow.button.resume",
					"label.post.magnet.to.chat",
					"label.init.save.loc.move",
					"label.assign.tags",
					"label.remove.tags",
					"menu.host.on.tracker",
					"menu.publish.on.tracker"};

			for ( int i=0; i<action_ids.length;i++ ){

				int	action_id = action_ids[i];

				if ( supportsAction( action_id)){

					boolean enabled = isActionEnabled( action_id );

					if ( enabled ){

						if ( action_id == TagFeatureExecOnAssign.ACTION_SCRIPT ){

							String script = getActionScript();

							if ( script.length() > 63 ){
								script = script.substring( 0, 60 ) + "...";
							}

							actions_str += (actions_str.length()==0?"":",") +
									MessageText.getString( action_keys[i]) + "=" + script;
							
						}else if ( 	action_id == TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS ||
									action_id == TagFeatureExecOnAssign.ACTION_REMOVE_TAGS ){
							
							List<Tag> tags = action_id == TagFeatureExecOnAssign.ACTION_ASSIGN_TAGS?getTagAssigns():getTagRemoves();
							
							String tag_str = "";
							
							for ( Tag t: tags ){
								
								tag_str += (tag_str==""?"":";") + t.getTagName( true );
							}
							
							actions_str += (actions_str.length()==0?"":",") +
									MessageText.getString( action_keys[i]) + "=" + tag_str ;
							
						}else{

							actions_str += (actions_str.length()==0?"":",") +
											MessageText.getString( action_keys[i]) + "=Y";
						}
					}
				}
			}

			if ( actions_str.length() > 0 ){

				text += actions_str;
			}
		}
		
		return( text );
	}
		// others

	@Override
	public void
	addTaggableBatch(
		boolean		starts )
	{
	}
	
	@Override
	public void
	addTaggable(
		Taggable	t )
	{
		t_listeners.dispatch( TL_ADD, t );

		tag_type.taggableAdded( this, t );

		tag_type.fireMembershipChanged( this );

		if ( tag_limits != null ){

			checkMaximumTaggables();
		}
	}

	@Override
	public void
	removeTaggable(
		Taggable	t )
	{
		t_listeners.dispatch( TL_REMOVE, t );

		tag_type.taggableRemoved( this, t );

		tag_type.fireMembershipChanged( this );

	}

	protected void
	sync()
	{
		t_listeners.dispatch( TL_SYNC, null );

		tag_type.taggableSync( this );

		savePersistentStuff();
	}

	protected void
	closing()
	{
		savePersistentStuff();
	}

	@Override
	public void
	removeTag()
	{
		boolean was_rss = isTagRSSFeedEnabled();

		tag_type.removeTag( this );

		if ( was_rss ){

			tag_type.getTagManager().checkRSSFeeds( this, false );
		}

		saveTransientStuff();
	}

	@Override
	public String
	getDescription()
	{
		return( description );
	}

	@Override
	public void
	setDescription(
		String		str )
	{
		String existing = getDescription();

		if ( existing == str ){

			return;

		}else if ( str == null || existing == null ){

		}else if ( str.equals( existing )){

			return;
		}

		description = str;

		boolean changed = writeStringAttribute( AT_DESCRIPTION, str );

		if (changed) {
			tag_type.fireMetadataChanged( this );
		}
	}

	@Override
	public void
	setTransientProperty(
		String property, Object value )
	{
		synchronized( this ){

			if ( transient_properties == null ){

				if ( value == null ){

					return;
				}

				transient_properties = new HashMap<>();
			}

			if ( value == null ){

				transient_properties.remove( property );

			}else{

				transient_properties.put( property, value );
			}

			tag_type.fireMetadataChanged( this );
		}
	}

	@Override
	public Object
	getTransientProperty(String property)
	{
		synchronized( this ){

			if ( transient_properties == null ){

				return( null );
			}

			return( transient_properties.get( property ));
		}
	}

	@Override
	public void
	addTagListener(
		TagListener	listener,
		boolean		fire_for_existing )
	{
		if (!t_listeners.hasListener(listener)) {
			t_listeners.addListener( listener );
		}

		if ( fire_for_existing ){

			for ( Taggable t: getTagged()){

				listener.taggableAdded( this, t );
			}
		}
	}

	protected void
	destroy()
	{
		Set<Taggable>	taggables = getTagged();

		for( Taggable t: taggables ){

			t_listeners.dispatch( TL_REMOVE, t );

			tag_type.taggableRemoved( this, t );
		}
	}

	@Override
	public void
	removeTagListener(
		TagListener	listener )
	{
		t_listeners.removeListener( listener );
	}

	@Override
	public List<com.biglybt.pif.tag.Taggable>
	getTaggables()
	{
		Set<Taggable> taggables = getTagged();

		List<com.biglybt.pif.tag.Taggable> result = new ArrayList<>(taggables.size());

		for ( Taggable t: taggables ){

			if ( t instanceof DownloadManager ){

				result.add(PluginCoreUtils.wrap((DownloadManager)t));
			}
		}

		return( result );
	}

	@Override
	public void
	requestAttention()
	{
		tag_type.requestAttention( this );
	}

	@Override
	public void
	addListener(
		final com.biglybt.pif.tag.TagListener listener )
	{
		synchronized( listener_map ){

			TagListener l = listener_map.get( listener );

			if ( l != null ){

				Debug.out( "listener already added" );

				return;
			}

			l = new TagListener() {

				@Override
				public void taggableSync(Tag tag) {
					listener.taggableSync(tag);
				}

				@Override
				public void taggableRemoved(Tag tag, Taggable tagged) {
					listener.taggableRemoved(tag, tagged);
				}

				@Override
				public void taggableAdded(Tag tag, Taggable tagged) {
					listener.taggableAdded(tag, tagged);
				}
			};

			listener_map.put( listener, l );

			addTagListener( l, false );
		}

	}

	@Override
	public void
	removeListener(
		com.biglybt.pif.tag.TagListener listener )
	{
		synchronized( listener_map ){

			TagListener l = listener_map.remove( listener );

			if ( l == null ){

				Debug.out( "listener not found" );

				return;
			}

			removeTagListener( l );
		}
	}

	protected Boolean
	readBooleanAttribute(
		String		attr,
		Boolean		def )
	{
		return( tag_type.readBooleanAttribute( this, attr, def ));
	}

	protected boolean
	writeBooleanAttribute(
		String	attr,
		Boolean	value )
	{
		return( tag_type.writeBooleanAttribute( this, attr, value ));
	}

	protected Long
	readLongAttribute(
		String	attr,
		Long	def )
	{
		return( tag_type.readLongAttribute( this, attr, def ));
	}

	protected boolean
	writeLongAttribute(
		String	attr,
		long	value )
	{
		return( tag_type.writeLongAttribute( this, attr, value ));
	}

	protected String
	readStringAttribute(
		String	attr,
		String	def )
	{
		return( tag_type.readStringAttribute( this, attr, def ));
	}

	/**
	 * @return Whether attribute was changed from existing value
	 */
	protected boolean
	writeStringAttribute(
		String	attr,
		String	value )
	{
		return tag_type.writeStringAttribute( this, attr, value );
	}

	protected Map<String,Object>
	readMapAttribute(
		String				attr,
		Map<String,Object>	def )
	{
		return( tag_type.readMapAttribute( this, attr, def ));
	}

	protected void
	writeMapAttribute(
		String				attr,
		Map<String,Object>	value )
	{
		tag_type.writeMapAttribute( this, attr, value );
	}
	
	protected String[]
	readStringListAttribute(
		String		attr,
		String[]	def )
	{
		return( tag_type.readStringListAttribute( this, attr, def ));
	}

	protected boolean
	writeStringListAttribute(
		String		attr,
		String[]	value )
	{
		return( tag_type.writeStringListAttribute( this, attr, value ));
	}

	protected long[]
	readLongListAttribute(
		String		attr,
		long[]		def )
	{
		return( tag_type.readLongListAttribute( this, attr, def ));
	}

	protected boolean
	writeLongListAttribute(
		String		attr,
		long[]		value )
	{
		return( tag_type.writeLongListAttribute( this, attr, value ));
	}
	
	private static final Map<Long,long[][]>	session_cache = new HashMap<>();

	private long[]						total_up_at_start;
	private long[]						total_down_at_start;

	private long[]						session_up;
	private long[]						session_down;

	private void
	loadTransientStuff()
	{
		if ( tag_rl != null && tag_rl.supportsTagRates()){

			synchronized( session_cache ){

				long[][] entry = session_cache.get( getTagUID());

				if ( entry != null ){

					total_up_at_start	= entry[0];
					total_down_at_start	= entry[1];
					session_up 			= entry[2];
					session_down 		= entry[3];
				}
			}
		}
	}

	private void
	saveTransientStuff()
	{
			// ipset tags get removed and then re-added when the schedule is updated so we need to
			// stash away their state and reload it when they get added back

		if ( tag_rl != null && tag_rl.supportsTagRates()){

			long[] session_up 		= getTagSessionUploadTotalRaw();
			long[] session_down 	= getTagSessionDownloadTotalRaw();

			synchronized( session_cache ){

				session_cache.put( getTagUID(), new long[][]{ total_up_at_start, total_down_at_start, session_up, session_down });
			}
		}
	}

	private void
	loadPersistentStuff()
	{
		if ( tag_rl != null && tag_rl.supportsTagRates()){

			String[] ups = readStringListAttribute( AT_BYTES_UP, null );

			if ( ups != null ){

				total_up_at_start = new long[ups.length];

				for ( int i=0;i<ups.length;i++){

					try{
						total_up_at_start[i] = Long.parseLong( ups[i] );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}

			String[] downs = readStringListAttribute( AT_BYTES_DOWN, null );

			if ( downs != null ){

				total_down_at_start = new long[downs.length];

				for ( int i=0;i<downs.length;i++){

					try{
						total_down_at_start[i] = Long.parseLong( downs[i] );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}
	}

	private void
	savePersistentStuff()
	{
		if ( tag_rl != null && tag_rl.supportsTagRates()){

			long[] session_up = getTagSessionUploadTotalRaw();

			if ( session_up != null ){

				String[] ups = new String[session_up.length];

				for ( int i=0;i<ups.length;i++ ){

					long l = session_up[i];

					if ( total_up_at_start != null && total_up_at_start.length > i ){

						l += total_up_at_start[i];
					}

					ups[i] = String.valueOf( l );
				}

				writeStringListAttribute( AT_BYTES_UP, ups );
			}

			long[] session_down = getTagSessionDownloadTotalRaw();

			if ( session_down != null ){

				String[] downs = new String[session_down.length];

				for ( int i=0;i<downs.length;i++ ){

					long l = session_down[i];

					if ( total_down_at_start != null && total_down_at_start.length > i ){

						l += total_down_at_start[i];
					}

					downs[i] = String.valueOf( l );
				}

				writeStringListAttribute( AT_BYTES_DOWN, downs );
			}
		}
	}

	public long[]
	getTagUploadTotal()
	{
		long[] result = getTagSessionUploadTotalRaw();

		if ( result != null ){

			if ( total_up_at_start != null && total_up_at_start.length == result.length ){

				for ( int i=0;i<result.length;i++ ){

					result[i] += total_up_at_start[i];
				}
			}
		}

		return( result );
	}

	private long[] session_up_reset;
	private long[] session_down_reset;

	public long[]
	getTagSessionUploadTotal()
	{
		long[] result = getTagSessionUploadTotalRaw();

		if ( result != null && session_up_reset != null && result.length == session_up_reset.length ){

			for ( int i=0;i<result.length;i++){

				result[i] -= session_up_reset[i];
			}
		}

		return( result );
	}

	public void
	resetTagSessionUploadTotal()
	{
		session_up_reset = getTagSessionUploadTotalRaw();
	}

	private long[]
	getTagSessionUploadTotalRaw()
	{
		if ( tag_rl == null || !tag_rl.supportsTagRates()){

			return( null );
		}

		long[] result =  getTagSessionUploadTotalCurrent();

		if ( result != null && session_up != null ){

			if ( result.length == session_up.length ){

				for ( int i=0;i<result.length;i++){

					result[i] += session_up[i];
				}
			}else{

				Debug.out( "derp" );
			}
		}

		return( result );
	}

	protected long[]
	getTagSessionUploadTotalCurrent()
	{
		return( null );
	}

	public long[]
	getTagDownloadTotal()
	{
		long[] result = getTagSessionDownloadTotalRaw();

		if ( result != null ){

			if ( total_down_at_start != null && total_down_at_start.length == result.length ){

				for ( int i=0;i<result.length;i++ ){

					result[i] += total_down_at_start[i];
				}
			}
		}

		return( result );
	}

	public long[]
	getTagSessionDownloadTotal()
	{
		long[] result = getTagSessionDownloadTotalRaw();

		if ( result != null && session_down_reset != null && result.length == session_down_reset.length ){

			for ( int i=0;i<result.length;i++){

				result[i] -= session_down_reset[i];
			}
		}

		return( result );
	}

	public void
	resetTagSessionDownloadTotal()
	{
		session_down_reset = getTagSessionDownloadTotalRaw();
	}

	private long[]
	getTagSessionDownloadTotalRaw()
	{
		if ( tag_rl == null || !tag_rl.supportsTagRates()){

			return( null );
		}

		long[] result =  getTagSessionDownloadTotalCurrent();

		if ( result != null && session_down != null ){

			if ( result.length == session_down.length ){

				for ( int i=0;i<result.length;i++){

					result[i] += session_down[i];
				}
			}else{

				Debug.out( "derp" );
			}
		}

		return( result );
	}

	protected long[]
	getTagSessionDownloadTotalCurrent()
	{
		return( null );
	}

	private static final int HISTORY_MAX_SECS = 30*60;
	private volatile boolean history_retention_required;
	private long[]	history;
	private int		history_pos;
	private boolean	history_wrapped;
	private boolean	timer_registered;

	public void
	setRecentHistoryRetention(
		boolean	required )
	{
		if ( tag_rl == null || !tag_rl.supportsTagRates()){

			return;
		}

		synchronized( this ){

			if ( required ){

				if ( !history_retention_required ){

					history 	= new long[HISTORY_MAX_SECS];

					history_pos	= 0;

					history_retention_required = true;

					if ( !timer_registered ){

						SimpleTimer.addTickReceiver( this );

						timer_registered = true;
					}
				}
			}else{

				history = null;

				history_retention_required = false;

				if ( timer_registered ){

					SimpleTimer.removeTickReceiver( this );

					timer_registered = false;
				}
			}
		}
	}

	public int[][]
 	getRecentHistory()
 	{
 		synchronized( this ){

 			if ( history == null ){

 				return( new int[2][0] );

 			}else{

 				int	entries = history_wrapped?HISTORY_MAX_SECS:history_pos;
 				int	start	= history_wrapped?history_pos:0;

 				int[][] result = new int[2][entries];

 				int	pos = start;

 				for ( int i=0;i<entries;i++){

 					if ( pos == HISTORY_MAX_SECS ){

 						pos = 0;
 					}

 					long entry = history[pos++];

 					int	send_rate 	= (int)((entry>>32)&0xffffffffL);
 					int	recv_rate 	= (int)((entry)    &0xffffffffL);

 					result[0][i] = send_rate;
 					result[1][i] = recv_rate;
  				}

 				return( result );
 			}
 		}
 	}

	@Override
	public long
	getTaggableAddedTime(
		Taggable taggble )
	{
		return( -1 );
	}


 	@Override
  public void
 	tick(
 		long	mono_now,
 		int 	count )
 	{
 		if ( !history_retention_required ){

 			return;
 		}

  		long send_rate 			= tag_rl.getTagCurrentUploadRate();
 		long receive_rate 		= tag_rl.getTagCurrentDownloadRate();

 		long	entry =
 			(((send_rate)<<32) & 0xffffffff00000000L ) |
 			(((receive_rate)   & 0x00000000ffffffffL ));


 		synchronized( this ){

 			if ( history != null ){

 				history[history_pos++] = entry;

 				if ( history_pos == HISTORY_MAX_SECS ){

 					history_pos 	= 0;
 					history_wrapped	= true;
 				}
 			}
 		}
 	}
 	
	public VuzeFile
	getVuzeFile()
	{
		return( getManager().getVuzeFile( this ));
	}

 	private class
 	TagPropertyImpl
 		implements TagProperty
 	{
 		private final String		name;
 		private final int			type;

 		private final CopyOnWriteList<TagPropertyListener>	listeners = new CopyOnWriteList<>();

 		private
 		TagPropertyImpl(
 			String		_name,
 			int			_type )
 		{
 			name		= _name;
 			type		= _type;
 		}

 		@Override
 		public Tag
 		getTag()
 		{
 			return( TagBase.this );
 		}

		@Override
		public int
		getType()
		{
			return( type );
		}

		@Override
		public String
		getName(
			boolean	localize )
		{
			if ( localize ){

				return( MessageText.getString( "tag.property." + name ));

			}else{

				return( name );
			}
		}

		@Override
		public boolean
		isEnabled()
		{
			return( readBooleanAttribute( AT_PROPERTY_PREFIX + "enabled." + name, true ));
		}
		
		@Override
		public void
		setEnabled(
			boolean	enabled )
		{
			if ( writeBooleanAttribute( AT_PROPERTY_PREFIX + "enabled." + name, enabled )){

				for ( TagPropertyListener l: listeners ){

					try{
						l.propertyChanged( this );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}

				tag_type.fireMetadataChanged( TagBase.this );
			}
		}
		
		@Override
		public void
		setStringList(
			String[]	value )
		{
			String name = getName( false );
			
			if ( name.equals( TagFeatureProperties.PR_CONSTRAINT )){
				
				String[] old_value = getStringList();
				
				if ( old_value.length == 0 || old_value[0].trim().isEmpty()){
					
					if ( value != null && value.length > 0 && !value[0].trim().isEmpty()){
				
						if ( !getTaggables().isEmpty()){
							
							UIManager ui_manager = StaticUtilities.getUIManager( 15*1000 );

							if ( ui_manager!= null ){
								
								String desc = MessageText.getString(
										"tag.constraint.with.manuals.desc",
										new String[]{ getTagName( true ) });
								
								long res = ui_manager.showMessageBox(
										"tag.constraint.with.manuals.title",
										"!" + desc + "!",
										UIManagerEvent.MT_OK | UIManagerEvent.MT_CANCEL );
	
								if ( res != UIManagerEvent.MT_OK ){
									
									return;
								}
							}
						}
					}
				}
			}
			
			if ( writeStringListAttribute( AT_PROPERTY_PREFIX + name, value )){

				for ( TagPropertyListener l: listeners ){

					try{
						l.propertyChanged( this );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}

				tag_type.fireMetadataChanged( TagBase.this );
			}
		}

		@Override
		public String[]
		getStringList()
		{
			return( readStringListAttribute( AT_PROPERTY_PREFIX + name, EMPTY_STRING_LIST ));
		}

		@Override
		public void
		setBoolean(
			Boolean	value )
		{
			if ( writeBooleanAttribute( AT_PROPERTY_PREFIX + name, value )){

				for ( TagPropertyListener l: listeners ){

					try{
						l.propertyChanged( this );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}

				tag_type.fireMetadataChanged( TagBase.this );
			}
		}

		@Override
		public Boolean
		getBoolean()
		{
			return( readBooleanAttribute( AT_PROPERTY_PREFIX + name, null ));
		}

		@Override
		public void
		setLong(
			Long	value )
		{
			if ( writeLongAttribute( AT_PROPERTY_PREFIX + name, value )){

				for ( TagPropertyListener l: listeners ){

					try{
						l.propertyChanged( this );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}

				tag_type.fireMetadataChanged( TagBase.this );
			}
		}

		@Override
		public Long
		getLong()
		{
			return( readLongAttribute( AT_PROPERTY_PREFIX + name, null ));
		}

		@Override
		public String
		getString()
		{
			String	value = null;

			switch( getType()){
				case TagFeatureProperties.PT_STRING_LIST:{
					String[] vals = getStringList();

					if ( vals != null && vals.length > 0 ){
						value = "";

						String name = getName( false );

						if ( name.equals( TagFeatureProperties.PR_TRACKER_TEMPLATES )){

							String str_merge 	= MessageText.getString("label.merge" );
							String str_replace 	= MessageText.getString("label.replace" );
							String str_remove 	= MessageText.getString("Button.remove" );

							for ( String val: vals ){
								String[] bits = val.split( ":" );
								String type = bits[0];
								String str 	= bits[1];

								if ( type.equals("m")){
									str += ": " + str_merge;
								}else if ( type.equals( "r" )){
									str += ": " + str_replace;
								}else{
									str += ": " + str_remove;
								}
								value += (value.length()==0?"":"," ) + str;
							}
						}else if ( name.equals( TagFeatureProperties.PR_CONSTRAINT )){

							value += vals[0];

							if ( value.trim().isEmpty()){
								
									// no constraint set
								
								value = null;
								
							}else{
								
								if ( vals.length > 1 ){
	
									String options = vals[1];
	
									boolean new_dls = options.contains( "am=3;" );
	
									if ( new_dls ){
									
										value += "," + MessageText.getString( "label.scope" );
										
										value += "=";
										
										value += MessageText.getString( "label.new.downloads" );
										
									}else{
										
										boolean auto_add 	= !options.contains( "am=2;" );
										boolean auto_remove = !options.contains( "am=1;" );
		
		
										if ( auto_add && auto_remove ){
		
										}else if ( auto_add || auto_remove ){
		
											value += "," + MessageText.getString( "label.scope" );
		
											value += "=";
		
											if ( auto_add ){
		
												value += MessageText.getString( "label.addition.only" );
		
											}else{
		
												value += MessageText.getString( "label.removal.only" );
											}
										}
									}
								}
							}
						}else{
							for ( String val: vals ){
								value += (value.length()==0?"":"," ) + val;
							}
						}
					}
					break;
				}
				case TagFeatureProperties.PT_BOOLEAN:{
					Boolean val = getBoolean();
					if ( val != null ){
						value = String.valueOf( val );
					}
					break;
				}
				case TagFeatureProperties.PT_LONG:{
					Long val = getLong();
					if ( val != null ){
						value = String.valueOf( val );
					}
					break;
				}
				default:{
					value = "Unknown type";
				}
			}

			if ( value == null ){

				return( "" );

			}else{

				return( getName( true ) + "=" + value );
			}
		}

		@Override
		public void
		addListener(
			TagPropertyListener		listener )
		{
			listeners.add( listener );
		}

		@Override
		public void
		removeListener(
			TagPropertyListener		listener )
		{
			listeners.remove( listener );
		}

		@Override
		public void
		syncListeners()
		{
			for ( TagPropertyListener l: listeners ){

				try{
					l.propertySync( this );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}
		
		@Override
		public String 
		explainTaggable(
			Taggable taggable)
		{
			return( tag_type.getTagManager().explain( TagBase.this, this, taggable ));
		}
 	}
 	
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( tag_name );

		try{
			writer.indent();

			tag_type.generateConfig( writer, this );

		}finally{

			writer.exdent();
		}
	}
}
