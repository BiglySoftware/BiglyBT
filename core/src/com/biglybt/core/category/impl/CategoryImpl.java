/*
 * File    : CategoryImpl.java
 * Created : 09 feb. 2004
 * By      : TuxPaper
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

package com.biglybt.core.category.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryListener;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagDownload;
import com.biglybt.core.tag.TagFeatureRunState;
import com.biglybt.core.tag.TagListener;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.tag.impl.TagBase;
import com.biglybt.core.util.*;

public class
CategoryImpl
	extends TagBase
	implements Category, Comparable, TagDownload, TagListener
{
  final String sName;
  private final int type;
  private final CopyOnWriteList<DownloadManager> managers_cow = new CopyOnWriteList<>();

  int upload_speed;
  int download_speed;

  private final Object UPLOAD_PRIORITY_KEY = new Object();

  private final Map<String,String>	attributes;

  private static final AtomicInteger	tag_ids = new AtomicInteger();

  private final LimitedRateGroup upload_limiter =
	  new LimitedRateGroup()
	  {
		  @Override
		  public String
		  getName()
		  {
			  return( "cat_up: " + sName);
		  }
		  @Override
		  public int
		  getRateLimitBytesPerSecond()
		  {
			  return( upload_speed );
		  }
		  @Override
		  public boolean isDisabled() {
			  return( upload_speed == -1 );
		  }

		  @Override
		  public void
		  updateBytesUsed(
				int	used )
		  {

		  }
	  };

  private final LimitedRateGroup download_limiter =
	  new LimitedRateGroup()
  {
	  @Override
	  public String
	  getName()
	  {
		  return( "cat_down: " + sName);
	  }
	  @Override
	  public int
	  getRateLimitBytesPerSecond()
	  {
		  return( download_speed );
	  }
	  @Override
	  public boolean isDisabled() {
		  return( download_speed == -1 );
	  }
	  @Override
	  public void
	  updateBytesUsed(
			int	used )
	  {

	  }
  };

  private boolean destroyed;

  private static final int LDT_CATEGORY_DMADDED     = 1;
  private static final int LDT_CATEGORY_DMREMOVED   = 2;
	private final ListenerManager<CategoryListener>	category_listeners = ListenerManager.createManager(
		"CatListenDispatcher",
		new ListenerManagerDispatcher<CategoryListener>()
		{
			@Override
			public void
			dispatch(
				CategoryListener		target,
				int						type,
				Object					value )
			{
				if ( type == LDT_CATEGORY_DMADDED )
					target.downloadManagerAdded((Category) CategoryImpl.this, (DownloadManager)value);
				else if ( type == LDT_CATEGORY_DMREMOVED )
					target.downloadManagerRemoved(CategoryImpl.this, (DownloadManager)value);
			}
		});

  protected CategoryImpl(CategoryManagerImpl manager, String sName, int maxup, int maxdown, Map<String,String> _attributes ) {
	super( manager, tag_ids.incrementAndGet(), sName );
	addTag();

    this.sName = sName;
    this.type = Category.TYPE_USER;
   
    setGroup( MessageText.getString( "TableColumn.header.category" ));
    
    upload_speed	= maxup;
    download_speed	= maxdown;
    attributes = _attributes;
    
    addTagListener( this, true );
  }

  protected CategoryImpl(CategoryManagerImpl manager, String sName, int type, Map<String,String> _attributes) {
	super( manager, tag_ids.incrementAndGet(), sName);
	addTag();

    this.sName = sName;
    this.type = type;
    
    setGroup( MessageText.getString( "TableColumn.header.category" ));

    attributes = _attributes;
  }

  @Override
  public void addCategoryListener(CategoryListener l) {
  	if (!category_listeners.hasListener(l)) {
  		category_listeners.addListener( l );
  	}
  }

  @Override
  public void removeCategoryListener(CategoryListener l) {
	  category_listeners.removeListener( l );
  }

	@Override
  public String getName() {
    return sName;
  }

  @Override
  public int getType() {
    return type;
  }
  
  @Override
  public boolean[] isTagAuto(){
	  if ( getType() == Category.TYPE_USER ){
		  return( new boolean[]{ false, false, false });
	  }else{
		  return( new boolean[]{ true, true, false });
	  }
  }
  
  @Override
  public List<DownloadManager> getDownloadManagers(List<DownloadManager> all_dms) {
	  if ( type == Category.TYPE_USER ){
		  return managers_cow.getList();
	  }else if ( type == Category.TYPE_ALL || all_dms == null ){
		  return all_dms;
	  }else{
		  List<DownloadManager> result = new ArrayList<>();
		  for (int i=0;i<all_dms.size();i++){
			  DownloadManager dm = all_dms.get(i);
			  Category cat = dm.getDownloadState().getCategory();
			  if ( cat == null || cat.getType() == Category.TYPE_UNCATEGORIZED){
				  result.add( dm );
			  }
		  }

		  return( result );
	  }
  }

  @Override
  public void addManager(DownloadManagerState manager_state) {
  	Category manager_cat = manager_state.getCategory();
		if ((type != Category.TYPE_UNCATEGORIZED && manager_cat != this)
				|| (type == Category.TYPE_UNCATEGORIZED && manager_cat != null)) {
    	manager_state.setCategory(this);
      // we will be called again by CategoryManager.categoryChange
      return;
    }

    DownloadManager	manager = manager_state.getDownloadManager();

    	// can be null if called during downloadmanagerstate construction
    if ( manager == null ){
    	return;
    }

    boolean do_add;
    
    synchronized( managers_cow ){
    	
    	do_add = !managers_cow.contains(manager);
    	
    	if ( do_add ){
    		
        	if ( type == Category.TYPE_USER ){
        		
        		managers_cow.add( manager );
        	}
    	}
    }
    
    if ( do_add ){
    	
        manager.addRateLimiter( upload_limiter, true );
        manager.addRateLimiter( download_limiter, false );
    }
    
    super.addTaggable( manager );

    if ( do_add ) {
  
      category_listeners.dispatch(LDT_CATEGORY_DMADDED, manager);
    }
  }

  @Override
  public void removeManager(DownloadManagerState manager_state) {
    if (manager_state.getCategory() == this) {
    	manager_state.setCategory(null);
      // we will be called again by CategoryManager.categoryChange
      return;
    }
    DownloadManager	manager = manager_state.getDownloadManager();

   	// can be null if called during downloadmanagerstate construction
    if ( manager == null ){
    	return;
    }

    boolean do_remove;
    
    synchronized( managers_cow ){
    	
    	do_remove = type != Category.TYPE_USER || managers_cow.contains(manager);
    
    	if ( do_remove ){
    	
    		managers_cow.remove(manager);
    	}
    }
    
    if ( do_remove ){

        manager.removeRateLimiter( upload_limiter, true );
        manager.removeRateLimiter( download_limiter, false );
    }
    
    super.removeTaggable( manager );

    if ( do_remove ) {
    	
      category_listeners.dispatch( LDT_CATEGORY_DMREMOVED, manager );
    }
  }

  
	@Override
	public void
	addTaggable(
		Taggable	t )
	{
		DownloadManager dm = (DownloadManager)t;
		
		addManager( dm.getDownloadState());
	}

	@Override
	public void
	removeTaggable(
		Taggable	t )
	{
		DownloadManager dm = (DownloadManager)t;
		
		removeManager( dm.getDownloadState());
	}
	
	public void
	taggableAdded(
		Tag			tag,
		Taggable	tagged )
	{
		DownloadManager manager = (DownloadManager)tagged;
		
		int pri = getIntAttribute( AT_UPLOAD_PRIORITY, -1 );

		if ( pri > 0 ){

			manager.updateAutoUploadPriority( UPLOAD_PRIORITY_KEY, true );
		}	
	}

	public void
	taggableSync(
		Tag			tag )
	{
	}

	public void
	taggableRemoved(
		Tag			tag,
		Taggable	tagged )
	{
		DownloadManager manager = (DownloadManager)tagged;
		
		int pri = getIntAttribute( AT_UPLOAD_PRIORITY, -1 );

		if ( pri > 0 ){

			manager.updateAutoUploadPriority( UPLOAD_PRIORITY_KEY, false );
		}
	}
	
  @Override
  public void
  setDownloadSpeed(
	int		speed )
  {
	  if ( download_speed != speed ){

		  download_speed = speed;

		  CategoryManagerImpl.getInstance().saveCategories(this);
	  }
  }

  @Override
  public int
  getDownloadSpeed()
  {
	  return( download_speed );
  }

  @Override
  public void
  setUploadSpeed(
	int		speed )
  {
	  if ( upload_speed != speed ){

		  upload_speed	= speed;

		  CategoryManagerImpl.getInstance().saveCategories(this);
	  }
  }

  @Override
  public int
  getUploadSpeed()
  {
	  return( upload_speed );
  }

  protected void
  setAttributes(
	Map<String,String> a )
  {
	  attributes.clear();
	  attributes.putAll( a );
  }

  protected Map<String,String>
  getAttributes()
  {
	  return( attributes );
  }

  @Override
  public String
  getStringAttribute(
	String		name )
  {
	  return( attributes.get(name));
  }

  @Override
  public void
  setStringAttribute(
	String		name,
	String		value )
  {
	  String old = attributes.put( name, value );

	  if ( old == null || !old.equals( value )){

		  CategoryManagerImpl.getInstance().saveCategories(this);
	  }

  }

  @Override
  public int
  getIntAttribute(
	String		name )
  {
	  return( getIntAttribute( name, 0 ));
  }

  private int
  getIntAttribute(
	String		name,
	int			def )
  {
	 String str = getStringAttribute( name );

	 if ( str == null ){
		 return( def );
	 }
	 return( Integer.parseInt( str ));
  }

  @Override
  public void
  setIntAttribute(
	String		name,
	int			value )
  {
	  String	str_val = String.valueOf( value );

	  String old = attributes.put( name, str_val );

	  if ( old == null || !old.equals( str_val )){

		  if ( name.equals( AT_UPLOAD_PRIORITY )){

			  for ( DownloadManager dm: managers_cow ){

				  dm.updateAutoUploadPriority( UPLOAD_PRIORITY_KEY, value > 0 );
			  }
		  }

		  CategoryManagerImpl.getInstance().saveCategories(this);
	  }

  }
  @Override
  public boolean
  getBooleanAttribute(
	String		name )
  {
	 String str = getStringAttribute( name );

	 return( str != null && str.equals( "true" ));
  }

  @Override
  public void
  setBooleanAttribute(
	String		name,
	boolean		value )
  {
	  String str_val = value?"true":"false";

	  String old = attributes.put( name, str_val );

	  if ( old == null || !old.equals( str_val )){

		  CategoryManagerImpl.getInstance().saveCategories(this);
	  }

  }

  @Override
  public int
  getTaggableTypes()
  {
	  return( Taggable.TT_DOWNLOAD );
  }

  @Override
  public String
  getTagName(
    boolean		localize )
  {
	  if ( localize ){
		  if ( type == Category.TYPE_ALL ||  type == Category.TYPE_UNCATEGORIZED){
			  return( MessageText.getString( getTagNameRaw()));
		  }
	  }
	  return( super.getTagName(localize));
  }

  @Override
  public boolean
  supportsTagRates()
  {
	  return( false );
  }

  @Override
  public boolean
  supportsTagUploadLimit()
  {
	  return( true );
  }

  @Override
  public boolean
  supportsTagDownloadLimit()
  {
	  return( true );
  }

  @Override
  public int
  getTagUploadLimit()
  {
	  return( getUploadSpeed());
  }

  @Override
  public void
  setTagUploadLimit(
		  int		bps )
  {
	  setUploadSpeed( bps );
  }

  @Override
  public int
  getTagCurrentUploadRate()
  {
	  return( -1 );
  }

  @Override
  public int
  getTagDownloadLimit()
  {
	  return( getDownloadSpeed());
  }

  @Override
  public void
  setTagDownloadLimit(
		  int		bps )
  {
	  setDownloadSpeed( bps );
  }

  @Override
  public int
  getTagCurrentDownloadRate()
  {
	  return( -1 );
  }

  @Override
  public int
  getTagUploadPriority()
  {
	  if ( type == Category.TYPE_USER ){

		  return( getIntAttribute( AT_UPLOAD_PRIORITY ));

	  }else{

		  return( -1 );
	  }
  }

  @Override
  public void
  setTagUploadPriority(
	  int		priority )
  {
	  setIntAttribute( AT_UPLOAD_PRIORITY, priority );
  }

  @Override
  public boolean
  getCanBePublicDefault()
  {
	  return( type == Category.TYPE_USER );
  }

  @Override
  public boolean
  supportsTagTranscode()
  {
	  return( false );
  }

  @Override
  public String[]
  getTagTranscodeTarget()
  {
	  return( null );
  }

  @Override
  public void
  setTagTranscodeTarget(
	  String		uid,
	  String		display_name )
  {
  }

  @Override
  public Set<DownloadManager>
  getTaggedDownloads()
  {
  	Core core = CoreFactory.getSingleton();

  	if ( !core.isStarted()){

  		return new IdentityHashSet<>();
  	}
	return(new IdentityHashSet<>(getDownloadManagers(core.getGlobalManager().getDownloadManagers())));
  }

  @Override
  public Set<Taggable>
  getTagged()
  {
	  return((Set<Taggable>)(Object)( getTaggedDownloads()));
  }

	@Override
	public int
	getTaggedCount()
	{
		return( getTagged().size());
	}

	@Override
	public boolean
	hasTaggable(
		Taggable	t )
	{
		if ( !( t instanceof DownloadManager )){

			return( false );
		}

	  	if ( type == Category.TYPE_USER ){

	  		return( managers_cow.contains((DownloadManager)t));

	  	}else if ( type == Category.TYPE_ALL ){

	  		return( true );

	  	}else{

	  		DownloadManager dm = (DownloadManager)t;

	  		Category cat = dm.getDownloadState().getCategory();

	  		if ( cat == null || cat.getType() == Category.TYPE_UNCATEGORIZED ){

	  			return( true );

	  		}else{

	  			return( false );
	  		}
	  	}
	}

	@Override
	public int
	getRunStateCapabilities()
	{
		return( TagFeatureRunState.RSC_NONE );
	}

	@Override
	public boolean
	hasRunStateCapability(
		int		capability )
	{
		return( false );
	}

	@Override
	public boolean[]
	getPerformableOperations(
   		int[]	ops )
	{
		return( new boolean[ ops.length]);
	}

	@Override
	public List<Taggable>
	performOperation(
		int		op )
	{
		Debug.out( "derp" );
		
		return( new ArrayList<>());
	}

  @Override
  protected void
  destroy()
  {
	  if ( !destroyed ){

		  destroyed = true;

		  removeTagListener( this );
		  
		  removeTag();		  
	  }
  }

	@Override
	public void removeTag() {
		CategoryManager.removeCategory(this);
		super.removeTag();
	}

	@Override
  public int compareTo(Object b)
  {
    boolean aTypeIsUser = type == Category.TYPE_USER;
    boolean bTypeIsUser = ((Category)b).getType() == Category.TYPE_USER;
    if (aTypeIsUser == bTypeIsUser)
      return sName.compareToIgnoreCase(((Category)b).getName());
    if (aTypeIsUser)
      return 1;
    return -1;
  }

  @Override
  public void dump(IndentWriter writer) {
	if ( upload_speed != 0 ){
		writer.println( "up=" + upload_speed );
	}
	if ( download_speed != 0 ){
		writer.println( "down=" + download_speed );
	}
	if ( attributes.size() > 0 ){

		writer.println( "attributes: " + attributes );
	}
	}
}
