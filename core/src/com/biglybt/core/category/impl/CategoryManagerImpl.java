/*
 * File    : CategoryManagerImpl.java
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

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryListener;
import com.biglybt.core.category.CategoryManagerListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagDownload;
import com.biglybt.core.tag.TagException;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.impl.TagTypeBase;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.core.xml.util.XMLEscapeWriter;
import com.biglybt.core.xml.util.XUXmlWriter;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.tracker.web.TrackerWebPageRequest;
import com.biglybt.pif.tracker.web.TrackerWebPageResponse;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.plugin.rssgen.RSSGeneratorPlugin;

public class
CategoryManagerImpl
	extends TagTypeBase
	implements RSSGeneratorPlugin.Provider, CategoryListener
{
  private static final int[] color_default = { 189, 178, 57 };

  private static final String PROVIDER = "categories";

  private static final String UNCAT_NAME 	= "__uncategorised__";
  private static final String ALL_NAME 		= "__all__";

  private static CategoryManagerImpl catMan;
  private static final AEMonitor	class_mon	= new AEMonitor( "CategoryManager:class" );

  
  private CategoryImpl catAll = null;
  private CategoryImpl catUncategorized = null;
  
  private boolean doneLoading = false;
 
  private final Map<String,CategoryImpl> categories 			= new HashMap<>();
  private final AEMonitor	categories_mon	= new AEMonitor( "Categories" );

  private final AtomicInteger	dms_with_cats = new AtomicInteger();
  
  private static final int LDT_CATEGORY_ADDED     = 1;
  private static final int LDT_CATEGORY_REMOVED   = 2;
  private static final int LDT_CATEGORY_CHANGED   = 3;
  private final ListenerManager category_listeners = ListenerManager.createManager(
    "CatListenDispatcher",
    new ListenerManagerDispatcher()
    {
      @Override
      public void
      dispatch(Object   _listener,
               int      type,
               Object   value )
      {
        CategoryManagerListener target = (CategoryManagerListener)_listener;

        if ( type == LDT_CATEGORY_ADDED )
          target.categoryAdded((Category)value);
        else if ( type == LDT_CATEGORY_REMOVED )
            target.categoryRemoved((Category)value);
        else if ( type == LDT_CATEGORY_CHANGED )
            target.categoryChanged((Category)value);
        }
    });

  private
  CategoryManagerImpl()
  {
	super( TagType.TT_DOWNLOAD_CATEGORY, TagDownload.FEATURES, "tag.type.category" );

	addTagType();

  	loadCategories();
  	
  	MessageText.addListener((l1,l2)->{
  		List<CategoryImpl>	cats;

		try{
			categories_mon.enter();

			cats = new ArrayList<>(categories.values());

		}finally{

			categories_mon.exit();
		}

		try{
			// buffer the changes to avoid costly multiple rebuilds of tagging UI components... 
			setMDEventsEnabled( false );
			
			for ( CategoryImpl c: cats ){
				c.localeChanged();
			}
			
		}finally{
			setMDEventsEnabled( true );
		}
  	});
  }

  boolean	md_events_enabled = true;
  List<Tag>	pending_md_events	= new ArrayList<>();
  
  private void
  setMDEventsEnabled(
	 boolean 	enabled )
  {
	  md_events_enabled = enabled;
	  
	  if ( enabled ){
		  for ( Tag t: pending_md_events ){
			  fireMetadataChanged(t);
		  }
		  pending_md_events.clear();
	  }
  }
  
  @Override
  protected void 
  fireMetadataChanged(
	Tag  t)
  {
	  if ( md_events_enabled ){
		  super.fireMetadataChanged(t);
	  }else{
		  pending_md_events.add( t );
	  }
	}
  
  public void addCategoryManagerListener(CategoryManagerListener l) {
    category_listeners.addListener( l );
  }

  public void removeCategoryManagerListener(CategoryManagerListener l) {
    category_listeners.removeListener( l );
  }

  public static CategoryManagerImpl getInstance() {
  	try{
  		class_mon.enter();
	    if (catMan == null)
	      catMan = new CategoryManagerImpl();
	    return catMan;
  	}finally{

  		class_mon.exit();
  	}
  }

  protected void loadCategories() {
    if (doneLoading)
      return;
    doneLoading = true;

    FileInputStream fin = null;
    BufferedInputStream bin = null;

    makeSpecialCategories();


    try {
      //open the file
      File configFile = FileUtil.getUserFile("categories.config");
      fin = FileUtil.newFileInputStream(configFile);
      bin = new BufferedInputStream(fin, 8192);

      Map map = BDecoder.decode(bin);

      List catList = (List) map.get("categories");
      for (int i = 0; i < catList.size(); i++) {
				Map mCategory = (Map) catList.get(i);
				String catName = new String((byte[]) mCategory.get("name"), Constants.DEFAULT_ENCODING_CHARSET);

				Long l_maxup = (Long) mCategory.get("maxup");
				Long l_maxdown = (Long) mCategory.get("maxdown");
				Map<String, String> attributes = BDecoder.decodeStrings((Map) mCategory.get("attr"));

				if (attributes == null) {
					attributes = new HashMap<>();
				}

				if (catName.equals(UNCAT_NAME)) {
					catUncategorized.setUploadSpeed(l_maxup == null ? 0 : l_maxup.intValue());
					catUncategorized.setDownloadSpeed(l_maxdown == null ? 0 : l_maxdown.intValue());
					catUncategorized.setAttributes(attributes);

				} else if (catName.equals(ALL_NAME)) {
					catAll.setAttributes(attributes);

				} else {
					CategoryImpl cat = new CategoryImpl(
							this,
							catName,
							l_maxup == null ? 0 : l_maxup.intValue(),
							l_maxdown == null ? 0 : l_maxdown.intValue(),
							attributes);
					
					cat.addCategoryListener( this );
					
					categories.put(catName,cat);
				}
      }
    }
    catch (FileNotFoundException e) {
      //Do nothing
    }
    catch (Exception e) {
    	Debug.printStackTrace( e );
    }
    finally {
      try {
        if (bin != null)
          bin.close();
      }
      catch (Exception e) {}
      try {
        if (fin != null)
          fin.close();
      }
      catch (Exception e) {}

      checkConfig();
    }
  }

  protected void saveCategories(Category category ){
	  saveCategories();

      category_listeners.dispatch( LDT_CATEGORY_CHANGED, category );
  }
  protected void saveCategories() {
    try{
    	categories_mon.enter();

      Map map = new HashMap();
      List list = new ArrayList(categories.size());

      Iterator<CategoryImpl> iter = categories.values().iterator();
      while (iter.hasNext()) {
        CategoryImpl cat = iter.next();

        if (cat.getType() == Category.TYPE_USER) {
          Map catMap = new HashMap();
          catMap.put( "name", cat.getName());
          catMap.put( "maxup", new Long(cat.getUploadSpeed()));
          catMap.put( "maxdown", new Long(cat.getDownloadSpeed()));
          catMap.put( "attr", cat.getAttributes());
          list.add(catMap);
        }
      }

      Map uncat = new HashMap();
      uncat.put( "name", UNCAT_NAME );
      uncat.put( "maxup", new Long(catUncategorized.getUploadSpeed()));
      uncat.put( "maxdown", new Long(catUncategorized.getDownloadSpeed()));
      uncat.put( "attr", catUncategorized.getAttributes());
      list.add( uncat );

      Map allcat = new HashMap();
      allcat.put( "name", ALL_NAME );
      allcat.put( "attr", catAll.getAttributes());
      list.add( allcat );

      map.put("categories", list);


      FileOutputStream fos = null;

      try {
        //encode the data
        byte[] torrentData = BEncoder.encode(map);

         File oldFile = FileUtil.getUserFile("categories.config");
         File newFile = FileUtil.getUserFile("categories.config.new");

         //write the data out
        fos = FileUtil.newFileOutputStream(newFile);
        fos.write(torrentData);
         fos.flush();
         fos.getFD().sync();

          //close the output stream
         fos.close();
         fos = null;

         //delete the old file
         if ( !oldFile.exists() || oldFile.delete() ) {
            //rename the new one
            newFile.renameTo(oldFile);
         }

      }
      catch (Exception e) {
      	Debug.printStackTrace( e );
      }
      finally {
        try {
          if (fos != null)
            fos.close();
        }
        catch (Exception e) {}
      }
    }finally{

    	checkConfig();

    	categories_mon.exit();
    }
  }

  public Category createCategory(String name) {
    makeSpecialCategories();
    CategoryImpl newCategory = getCategory(name);
    if (newCategory == null) {
      newCategory = new CategoryImpl(this,name, 0, 0, new HashMap<String,String>());
      newCategory.addCategoryListener( this );
      categories.put(name, newCategory);
      saveCategories();

      category_listeners.dispatch( LDT_CATEGORY_ADDED, newCategory );
      return (Category)categories.get(name);
    }
    return newCategory;
  }

  public void removeCategory(Category category) {
    if (categories.containsKey(category.getName())) {
      CategoryImpl old = categories.remove(category.getName());
      saveCategories();
      category_listeners.dispatch( LDT_CATEGORY_REMOVED, category );

      if ( old != null ){
    	  old.destroy();
      }
    }
  }

  public Category[] getCategories() {
    if (categories.size() > 0)
      return (Category[])categories.values().toArray(new Category[categories.size()]);
    return (new Category[0]);
  }

  public CategoryImpl getCategory(String name) {
    return categories.get(name);
  }

  public Category getCategory(int type) {
    if (type == Category.TYPE_ALL)
      return catAll;
    if (type == Category.TYPE_UNCATEGORIZED)
      return catUncategorized;
    return null;
  }

  private void makeSpecialCategories() {
    if (catAll == null) {
      catAll = new CategoryImpl(this,"Categories.all", Category.TYPE_ALL, new HashMap<String,String>());
      categories.put("Categories.all", catAll);
    }

    if (catUncategorized == null) {
      catUncategorized = new CategoryImpl(this,"Categories.uncategorized", Category.TYPE_UNCATEGORIZED, new HashMap<String,String>());
      categories.put("Categories.uncategorized", catUncategorized);
    }
  }

  
  public void downloadManagerAdded(Category cat, DownloadManager manager){
	  
	  dms_with_cats.incrementAndGet();
  }

	
  public void downloadManagerRemoved(Category cat, DownloadManager manager){
	 
	  dms_with_cats.decrementAndGet();
  }
	
  public int getCategorisedDownloadCount(){
	  int num = dms_with_cats.get();
	  if ( num < 0 ){	// shouldn't happen...
		  num = 0;
	  }
	  return( num );
  }
  @Override
  public int[]
  getColorDefault()
  {
	  return( color_default );
  }

  @Override
  public int getTagCount(){
	  return( categories.size());
  }
  
  @Override
  public List<Tag>
  getTags()
  {
	  return( new ArrayList<Tag>( categories.values()));
  }
  
  @Override
  public Tag
  createTag(
	  String 	name,
	  boolean	auto_add )

	  throws TagException
  {
	  if ( !auto_add ){
		  
		  throw( new TagException( "Not supported - must be auto-add" ));
	  }
	  
	  Category cat = getCategory( name );
	  
	  if ( cat == null ){
		  
		  cat = createCategory( name );
	  }
	  
	  return( cat );
  }
	
  @Override
  protected void
  sync()
  {
	  super.sync();
  }

  private void
  checkConfig()
  {
	  boolean	gen_enabled = false;

	  for ( CategoryImpl cat: categories.values()){

		  if ( cat.getBooleanAttribute( Category.AT_RSS_GEN )){

			  gen_enabled = true;

			  break;
		  }
	  }

	  if ( gen_enabled ){

		  RSSGeneratorPlugin.registerProvider( PROVIDER, this  );

	  }else{

		  RSSGeneratorPlugin.unregisterProvider( PROVIDER );
	  }
  }

	@Override
	public boolean
	isEnabled()
	{
		return( true );
	}

	@Override
	public boolean
	generate(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		URL	url	= request.getAbsoluteURL();

		String path = url.getPath();

		int	pos = path.indexOf( '?' );

		if ( pos != -1 ){

			path = path.substring(0,pos);
		}

		path = path.substring( PROVIDER.length()+1);

		XMLEscapeWriter pw = new XMLEscapeWriter( new PrintWriter(new OutputStreamWriter( response.getOutputStream(), "UTF-8" )));

		pw.setEnabled( false );

		if ( path.length() <= 1 ){

			response.setContentType( "text/html; charset=UTF-8" );

			pw.println( "<HTML><HEAD><TITLE>" + Constants.APP_NAME + " Category Feeds</TITLE></HEAD><BODY>" );

			Map<String,String>	lines = new TreeMap<>();

			List<CategoryImpl>	cats;

			try{
				categories_mon.enter();

				cats = new ArrayList<>(categories.values());

			}finally{

				categories_mon.exit();
			}

			for ( CategoryImpl c: cats ){

				if ( c.getBooleanAttribute( Category.AT_RSS_GEN )){

					String	name = getDisplayName( c );

					String	cat_url = PROVIDER + "/" + URLEncoder.encode( c.getName(), "UTF-8" );

					lines.put( name, "<LI><A href=\"" + cat_url + "\">" + name + "</A></LI>" );
				}
			}

			for ( String line: lines.values() ){

				pw.println( line );
			}

			pw.println( "</BODY></HTML>" );

		}else{

			String	cat_name = URLDecoder.decode( path.substring( 1 ), "UTF-8" );

			CategoryImpl	cat;

			try{
				categories_mon.enter();

				cat = categories.get( cat_name );

			}finally{

				categories_mon.exit();
			}

			if ( cat == null ){

				response.setReplyStatus( 404 );

				return( true );
			}

			List<DownloadManager> dms = cat.getDownloadManagers( CoreFactory.getSingleton().getGlobalManager().getDownloadManagers());

			List<Download> downloads = new ArrayList<>(dms.size());

			long	dl_marker = 0;

			for ( DownloadManager dm: dms ){

				TOTorrent torrent = dm.getTorrent();

				if ( torrent == null ){

					continue;
				}

				if ( !TorrentUtils.isReallyPrivate( torrent )){

					dl_marker += dm.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );

					downloads.add( PluginCoreUtils.wrap(dm));
				}
			}

			String	config_key = "cat.rss.config." + Base32.encode( cat.getName().getBytes( "UTF-8" ));

			long	old_marker = COConfigurationManager.getLongParameter( config_key + ".marker", 0 );

			long	last_modified = COConfigurationManager.getLongParameter( config_key + ".last_mod", 0 );

			long now = SystemTime.getCurrentTime();

			if ( old_marker == dl_marker ){

				if ( last_modified == 0 ){

					last_modified = now;
				}
			}else{

				COConfigurationManager.setParameter( config_key + ".marker", dl_marker );

				last_modified = now;
			}

			if ( last_modified == now ){

				COConfigurationManager.setParameter( config_key + ".last_mod", last_modified );
			}

			response.setContentType( "application/xml; charset=UTF-8" );

			pw.println( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );

			pw.println( "<rss version=\"2.0\" " + Constants.XMLNS_VUZE + ">" );

			pw.println( "<channel>" );

			pw.println( "<title>" + escape( getDisplayName( cat )) + "</title>" );

			Collections.sort(
					downloads,
				new Comparator<Download>()
				{
					@Override
					public int
					compare(
						Download d1,
						Download d2)
					{
						long	added1 = getAddedTime( d1 )/1000;
						long	added2 = getAddedTime( d2 )/1000;

						return((int)(added2 - added1 ));
					}
				});


			pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( last_modified ) + "</pubDate>" );

			for (int i=0;i<downloads.size();i++){

				Download download = downloads.get( i );

				DownloadManager	core_download = PluginCoreUtils.unwrap( download );

				Torrent torrent = download.getTorrent();

				byte[] hash = torrent.getHash();

				String	hash_str = Base32.encode( hash );

				pw.println( "<item>" );

				pw.println( "<title>" + escape( download.getName()) + "</title>" );

				pw.println( "<guid>" + hash_str + "</guid>" );

				String magnet_url = escape( UrlUtils.getMagnetURI( download ));

				pw.println( "<link>" + magnet_url + "</link>" );

				long added = core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);

				pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate( added ) + "</pubDate>" );

				pw.println(	"<vuze:size>" + torrent.getSize()+ "</vuze:size>" );
				pw.println(	"<vuze:assethash>" + hash_str + "</vuze:assethash>" );

				pw.println( "<vuze:downloadurl>" + magnet_url + "</vuze:downloadurl>" );

				DownloadScrapeResult scrape = download.getLastScrapeResult();

				if ( scrape != null && scrape.getResponseType() == DownloadScrapeResult.RT_SUCCESS ){

					pw.println(	"<vuze:seeds>" + scrape.getSeedCount() + "</vuze:seeds>" );
					pw.println(	"<vuze:peers>" + scrape.getNonSeedCount() + "</vuze:peers>" );
				}

				String dl_cat = download.getCategoryName();
				
				if ( dl_cat != null && dl_cat.length() > 0 ){

					if ( !dl_cat.equalsIgnoreCase("Categories.uncategorized")){
						
						pw.println( "<category>" + escape( dl_cat ) + "</category>" );
					}
				}
				
				List<Tag> dl_tags = TagManagerFactory.getTagManager().getTagsForTaggable( core_download );

				for ( Tag dl_tag : dl_tags) {

					TagType tt = dl_tag.getTagType();
					
					if ( tt.isTagTypeAuto() || tt.getTagType() != TagType.TT_DOWNLOAD_MANUAL ){
						
						continue;
					}
					
					boolean[] autos = dl_tag.isTagAuto();
					
					if (!( autos[0] || autos[1] )){
						
						pw.println( "<tag>" + escape( dl_tag.getTagName(true )) + "</tag>" );
					}
				}
				
				pw.println( "</item>" );
			}

			pw.println( "</channel>" );

			pw.println( "</rss>" );
		}

		pw.flush();

		return( true );
	}

	private String
	getDisplayName(
		CategoryImpl	c )
	{
		if ( c == catAll ){

			return( MessageText.getString( "Categories.all" ));

		}else if ( c == catUncategorized ){

			return( MessageText.getString( "Categories.uncategorized" ));

		}else{

			return( c.getName());
		}
	}

	protected long
	getAddedTime(
		Download	download )
	{
		DownloadManager	core_download = PluginCoreUtils.unwrap( download );

		return( core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME));
	}

	protected String
	escape(
		String	str )
	{
		return( XUXmlWriter.escapeXML(str));
	}
}
