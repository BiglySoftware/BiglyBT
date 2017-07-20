/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 *
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
 */

package com.biglybt.ui.swt.browser.listener;

import java.io.File;
import java.net.URL;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.util.TorrentUtils.torrentAttributeListener;
import com.biglybt.util.JSONUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.custom.CustomizationManagerFactory;
import com.biglybt.core.messenger.browser.BrowserMessage;
import com.biglybt.core.messenger.browser.listeners.AbstractBrowserMessageListener;
import com.biglybt.core.metasearch.*;
import com.biglybt.core.metasearch.impl.web.CookieParser;
import com.biglybt.core.metasearch.impl.web.WebEngine;
import com.biglybt.core.subs.Subscription;
import com.biglybt.core.subs.SubscriptionDownloadListener;
import com.biglybt.core.subs.SubscriptionException;
import com.biglybt.core.subs.SubscriptionHistory;
import com.biglybt.core.subs.SubscriptionManagerFactory;
import com.biglybt.core.subs.SubscriptionResult;
import com.biglybt.core.vuzefile.VuzeFile;
import com.biglybt.core.vuzefile.VuzeFileComponent;
import com.biglybt.core.vuzefile.VuzeFileHandler;
import com.biglybt.ui.swt.browser.OpenCloseSearchDetailsListener;
import com.biglybt.ui.swt.views.skin.TorrentListViewsUtils;

public class MetaSearchListener extends AbstractBrowserMessageListener {

	public static final String LISTENER_ID = "metasearch";

	public static final String OP_SEARCH				= "search";

	public static final String OP_GET_ENGINES 			= "get-engines";
	public static final String OP_GET_ALL_ENGINES 		= "get-all-engines";
	public static final String OP_ENGINE_PREFERRED		= "engine-preferred";

	public static final String OP_CHANGE_ENGINE_SELECTION 	= "change-engine-selection";

	public static final String OP_SET_SELECTED_ENGINES 	= "set-selected-engines";

	public static final String OP_GET_AUTO_MODE		 	= "get-auto-mode";

	public static final String OP_SAVE_TEMPLATE		 	= "save-template";
	public static final String OP_LOAD_TEMPLATE		 	= "load-template";
	public static final String OP_DELETE_TEMPLATE		= "delete-template";
	public static final String OP_TEST_TEMPLATE			= "test-template";

	public static final String OP_EXPORT_TEMPLATE		= "export-template";
	public static final String OP_IMPORT_TEMPLATE		= "import-template";

	public static final String OP_OPEN_SEARCH_RESULTS	= "open-search-results";
	public static final String OP_CLOSE_SEARCH_RESULTS	= "close-search-results";

	public static final String OP_LOAD_TORRENT			= "load-torrent";
	public static final String OP_HAS_LOAD_TORRENT		= "has-load-torrent";

	public static final String OP_ENGINE_LOGIN			= "engine-login";
	public static final String OP_GET_LOGIN_COOKIES		= "get-login-cookies";

	public static final String OP_CREATE_SUBSCRIPTION   		= "create-subscription";
	public static final String OP_READ_SUBSCRIPTION   			= "read-subscription";
	public static final String OP_UPDATE_SUBSCRIPTION   		= "update-subscription";
	public static final String OP_READ_SUBSCRIPTION_RESULTS   	= "read-subscription-results";
	public static final String OP_DELETE_SUBSCRIPTION_RESULTS   = "delete-subscription-results";
	public static final String OP_MARK_SUBSCRIPTION_RESULTS	   	= "mark-subscription-results";
	public static final String OP_DOWNLOAD_SUBSCRIPTION   		= "download-subscription";
	public static final String OP_SUBSCRIPTION_SET_AUTODL   	= "subscription-set-auto-download";

	public static final String OP_IS_CUSTOMISED   				= "is-customized";

	public static final String OP_ADD_EXTERNAL_LINKS   				= "add-external-links";

	private static final Set	active_subs_auth = new HashSet();

	private final OpenCloseSearchDetailsListener openCloseSearchDetailsListener;


	public
	MetaSearchListener(
		OpenCloseSearchDetailsListener openCloseSearchDetailsListener )
	{
		super(LISTENER_ID);

		this.openCloseSearchDetailsListener = openCloseSearchDetailsListener;
	}

	@Override
	public void handleMessage(BrowserMessage message) {

		String opid = message.getOperationId();

		MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();

		metaSearchManager.log( "BrowserListener: received " + message );

		if (OP_SEARCH.equals(opid)) {

			Map decodedMap = message.getDecodedMap();


			search( decodedMap, null );

		}else if ( OP_ENGINE_PREFERRED.equals( opid )){

			final Map decodedMap = message.getDecodedMap();

			long engine_id = ((Long)decodedMap.get("engine_id")).longValue();

			Engine engine = getEngineFromId( engine_id );

			if ( engine != null ){

				metaSearchManager.getMetaSearch().enginePreferred( engine );
			}

		} else if(OP_ENGINE_LOGIN.equals(opid)) {

			final Map decodedMap = message.getDecodedMap();

			long engine_id = ((Long)decodedMap.get("engine_id")).longValue();

			final Long	sid = (Long)decodedMap.get( "sid" );

			final Engine engine = getEngineFromId(engine_id);

			if ( engine != null && engine instanceof WebEngine){

				final WebEngine webEngine = (WebEngine) engine;

				Utils.execSWTThread( new Runnable() {
					@Override
					public void run() {
						new ExternalLoginWindow(
							new ExternalLoginListener()
							{
								private String previous_cookies;

								private boolean	search_done;

								@Override
								public void
								canceled(
									ExternalLoginWindow 	window )
								{
									/* gouss doesn't wan't anything on cancel
									if ( !outcome_informed ){

										outcome_informed = true;

										Map params = getParams( webEngine );

										params.put( "error", "operation cancelled" );

										sendBrowserMessage("metasearch", "engineFailed", params );
									}
									*/
								}

								@Override
								public void
								cookiesFound(
									ExternalLoginWindow 	window,
									String 					cookies )
								{
									if ( handleCookies( cookies, false )){

										window.close();
									}
								}

								@Override
								public void
								done(
									ExternalLoginWindow 	window,
									String 					cookies )
								{
									handleCookies( cookies, true );

									/*
									if ( !outcome_informed ){

										outcome_informed = true;

										Map params = getParams( webEngine );

										sendBrowserMessage("metasearch", "engineCompleted", params );
									}
									*/
								}

								private boolean
								handleCookies(
									String		cookies,
									boolean		force_if_ready )
								{
									if ( search_done ){

										return( false );
									}

									String[] required = webEngine.getRequiredCookies();

									boolean	skip_search = required.length == 0 && !force_if_ready;

									if ( CookieParser.cookiesContain( required, cookies )){

										webEngine.setCookies(cookies);

										if ( previous_cookies == null || !previous_cookies.equals( cookies )){

											previous_cookies = cookies;

											if ( !skip_search ){

													// search operation will report outcome

												search_done	= true;

												search( decodedMap, webEngine );
											}
										}
									}

									return( search_done );
								}

								/*
								protected Map
								getParams(
									Engine	engine )
								{
									Map<String, Object> params = new HashMap<>();
									params.put("id", new Long(engine.getId()));
									params.put("name", engine.getName());
									params.put("favicon", engine.getIcon());
									params.put("dl_link_css", engine.getDownloadLinkCSS());
									params.put("shareable", new Boolean( engine.isShareable()));

									if ( sid != null ){
										params.put( "sid", sid );
									}
									return( params );
								}
								*/
							},
						webEngine.getName(),
						webEngine.getLoginPageUrl(),
						false,
						webEngine.getAuthMethod(),
						engine.isMine());
					}
				});
			}else{

				Map<String, Object> params = new HashMap<>();

				if ( sid != null ){
					params.put( "sid", sid );
				}
				params.put( "error", "engine not found or not a web engine" );

				sendBrowserMessage("metasearch", "engineFailed", params );
			}
		} else if(OP_GET_LOGIN_COOKIES.equals(opid)) {

			final Map decodedMap = message.getDecodedMap();

			final String url = ((String) decodedMap.get("url")).replaceAll("%s", "");

			Utils.execSWTThread( new Runnable() {
				@Override
				public void run() {
					new ExternalLoginWindow(
						new ExternalLoginListener()
						{
						@Override
						public void canceled(ExternalLoginWindow window) {
							sendBrowserMessage("metasearch", "setCookiesFailed", new HashMap() );
						}

							@Override
						public void cookiesFound(ExternalLoginWindow window, String cookies) {}

							@Override
						public void done(ExternalLoginWindow window, String cookies) {
							String[] cookieNames = CookieParser.getCookiesNames(cookies);
							Map<String, Object> params = new HashMap<>();
							params.put("cookieNames", cookieNames);
							params.put("currentCookie",cookies);
							params.put("cookieMethod", window.proxyCaptureModeRequired()?WebEngine.AM_PROXY:WebEngine.AM_TRANSPARENT );
							sendBrowserMessage("metasearch", "setCookies", params );
						}
						},
						url,
						url,
						true,
						WebEngine.AM_PROXY,
						true );
				}
			});


		} else if(OP_GET_ENGINES.equals(opid)) {
			String subscriptionId  = null;

			try {
				final Map decodedMap = message.getDecodedMap();

				subscriptionId		= ((String)decodedMap.get("subs_id"));
			} catch(Exception e) {
				//No parameters
			}

			Engine[] engines = null;

			if ( subscriptionId != null ){

				engines = new Engine[0];

				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( subscriptionId );

				if ( subs != null ){

					try{
						Engine engine = subs.getEngine();

						if ( engine != null ){

							engines = new Engine[]{ engine };
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}

			if ( engines == null ){

				engines = metaSearchManager.getMetaSearch().getEngines( true, true );
			}

			List params = new ArrayList();
			for(int i = 0 ; i < engines.length ; i++) {
				Engine engine = engines[i];

				if ( !engine.isActive() || engine.getSource() == Engine.ENGINE_SOURCE_UNKNOWN ){

						// don't skip if this is an explicit get

					if ( subscriptionId == null ){

						continue;
					}
				}
				Map<String, Object> engineMap = new HashMap<>();
				engineMap.put("id", new Long(engine.getId()));
				engineMap.put("name", engine.getName());
				engineMap.put("favicon", engine.getIcon());
				engineMap.put("dl_link_css", engine.getDownloadLinkCSS());
				engineMap.put("selected", Engine.SEL_STATE_STRINGS[ engine.getSelectionState()]);
				engineMap.put("type", Engine.ENGINE_SOURCE_STRS[ engine.getSource()]);
				engineMap.put("shareable", Boolean.valueOf(engine.isShareable()));
				params.add(engineMap);
			}
			sendBrowserMessage("metasearch", "enginesUsed",params);

		} else if(OP_GET_ALL_ENGINES.equals(opid)) {

			Engine[] engines = metaSearchManager.getMetaSearch().getEngines( false, true );
			List params = new ArrayList();
			for(int i = 0 ; i < engines.length ; i++) {
				Engine engine = engines[i];

				if ( engine.getSource() == Engine.ENGINE_SOURCE_UNKNOWN ){
					continue;
				}

				Map<String, Object> engineMap = new HashMap<>();
				engineMap.put("id", new Long(engine.getId()));
				engineMap.put("name", engine.getName());
				engineMap.put("favicon", engine.getIcon());
				engineMap.put("dl_link_css", engine.getDownloadLinkCSS());
				engineMap.put("selected", Engine.SEL_STATE_STRINGS[ engine.getSelectionState()]);
				engineMap.put("type", Engine.ENGINE_SOURCE_STRS[ engine.getSource()]);
				engineMap.put("shareable", Boolean.valueOf(engine.isShareable()));
				params.add(engineMap);
			}
			sendBrowserMessage("metasearch", "engineList",params);

		} else if( OP_SET_SELECTED_ENGINES.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			List template_ids = (List)decodedMap.get( "template_ids" );

			long[] ids = new long[template_ids.size()];

			for (int i=0;i<ids.length;i++ ){

				Map m = (Map)template_ids.get(i);

				ids[i] = ((Long)m.get("id")).longValue();
			}

			boolean	auto = ((Boolean)decodedMap.get( "auto" )).booleanValue();

				// there's some code that attempts to switch to 'auto=true' on first use as
				// when 3110 defaults to false and the decision was made to switch this
				// disable the behaviour if we are customised


			Boolean	is_default = (Boolean)decodedMap.get( "set_default" );

			boolean skip = false;

			if ( is_default != null && is_default.booleanValue()){

				if ( CustomizationManagerFactory.getSingleton().getActiveCustomization() != null ){

					skip = true;
				}
			}

			try{
				if ( !skip ){

					metaSearchManager.setSelectedEngines( ids, auto );
				}

				Map<String, Object> params = new HashMap<>();
				sendBrowserMessage("metasearch", "setSelectedCompleted",params);

			}catch( Throwable e ){

				Debug.out( e );

				Map<String, Object> params = new HashMap<>();
				params.put("error",Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "setSelectedFailed",params);
			}
		} else if( OP_CHANGE_ENGINE_SELECTION.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			MetaSearch ms = metaSearchManager.getMetaSearch();

			Engine[] engines = ms.getEngines( false, true );

			Set selected = new HashSet();

			for (int i=0;i<engines.length;i++){

				Engine e = engines[i];

				if ( e.getSelectionState() == Engine.SEL_STATE_MANUAL_SELECTED ){

					selected.add( new Long( e.getId()));
				}
			}

			List l_engines = (List)decodedMap.get( "engines" );

			for (int i=0;i<l_engines.size();i++){

				Map	map = (Map)l_engines.get(i);

				long id = ((Long)map.get("id")).longValue();

				String str = (String)map.get( "selected");

				if ( str.equalsIgnoreCase( Engine.SEL_STATE_STRINGS[Engine.SEL_STATE_MANUAL_SELECTED])){

					selected.add( new Long( id ));

				}else if ( str.equalsIgnoreCase( Engine.SEL_STATE_STRINGS[Engine.SEL_STATE_DESELECTED])){

					selected.remove( new Long( id ));
				}
			}

			long[] ids = new long[selected.size()];

			Iterator it = selected.iterator();

			int	pos = 0;

			while( it.hasNext()){

				long	 id = ((Long)it.next()).longValue();

				ids[pos++] = id;
			}

			try{
				metaSearchManager.setSelectedEngines( ids, metaSearchManager.isAutoMode());

				Map<String, Object> params = new HashMap<>();
				sendBrowserMessage("metasearch", "changeEngineSelectionCompleted",params);

			}catch( Throwable e ){

				Debug.out( e );

				Map<String, Object> params = new HashMap<>();
				params.put("error",Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "changeEngineSelectionFailed",params);
			}
		} else if(OP_GET_AUTO_MODE.equals(opid)) {

			boolean mode = metaSearchManager.isAutoMode();

			Map<String, Object> params = new HashMap<>();

			params.put( "auto", Boolean.valueOf(mode));

			boolean custom = CustomizationManagerFactory.getSingleton().getActiveCustomization() != null;

			params.put( "is_custom", Boolean.valueOf(custom));

			sendBrowserMessage("metasearch", "getAutoModeResult",params);

		} else if( OP_SAVE_TEMPLATE.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			String	type_str = (String)decodedMap.get( "type" );

			String	name = (String)decodedMap.get( "name" );

			Long	l_id	= (Long)decodedMap.get( "id" );

			long	id = l_id == null?-1:l_id.longValue();

			String	json = (String)decodedMap.get( "value" );

			String	cookies = (String)decodedMap.get( "current_cookie" );


			try{
				Engine engine =
					metaSearchManager.addEngine(
							id,
							type_str.equals( "json" )?Engine.ENGINE_TYPE_JSON:Engine.ENGINE_TYPE_REGEX,
							name,
							json );

				engine.setMine( true );

				if(cookies!= null && engine instanceof WebEngine) {
					WebEngine we = (WebEngine) engine;
					we.setCookies(cookies);
				}

				Map<String, Object> params = new HashMap<>();
				params.put( "id", new Long( engine.getId() ));

				sendBrowserMessage( "metasearch", "saveTemplateCompleted", params );

			}catch( Throwable e ){

				Debug.out( e );

				Map<String, Object> params = new HashMap<>();
				params.put( "id", new Long( id ));
				params.put("error",Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "saveTemplateFailed",params);
			}
		} else if( OP_LOAD_TEMPLATE.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			long	id	= ((Long)decodedMap.get( "id" )).longValue();

			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );

			if ( engine == null ){

				Map<String, Object> params = new HashMap<>();
				params.put( "id", new Long( id ));
				params.put("error","Template not found");

				sendBrowserMessage("metasearch", "loadTemplateFailed",params);

			}else{

				try{
					Map<String, Object> params = new HashMap<>();
					params.put("id", new Long(engine.getId()));
					params.put("name", engine.getName());
					int type = engine.getType();
					params.put("type", type < Engine.ENGINE_TYPE_STRS.length ? Engine.ENGINE_TYPE_STRS[type] : type);
					params.put("value", JSONObject.escape( engine.exportToJSONString()));
					params.put("shareable", Boolean.valueOf(engine.isShareable()));

					params.put("uid", engine.getUID());

					params.put("supports_direct_download",
						Boolean.valueOf(engine.supportsField(Engine.FIELD_TORRENTLINK) ||
							engine.supportsField(Engine.FIELD_DOWNLOADBTNLINK)));

					params.put( "auto_dl_supported", Boolean.valueOf(engine.getAutoDownloadSupported() == Engine.AUTO_DL_SUPPORTED_YES));

					sendBrowserMessage( "metasearch", "loadTemplateCompleted", params );

				}catch( Throwable e ){

					Debug.out( e );

					Map<String, Object> params = new HashMap<>();
					params.put( "id", new Long( id ));
					params.put("error",Debug.getNestedExceptionMessage(e));

					sendBrowserMessage("metasearch", "loadTemplateFailed",params);
				}
			}
		} else if( OP_DELETE_TEMPLATE.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			long	id	= ((Long)decodedMap.get( "id" )).longValue();

			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );

			if ( engine == null ){

				Map<String, Object> params = new HashMap<>();
				params.put( "id", new Long( id ));
				params.put( "error", "Template not found" );

				sendBrowserMessage("metasearch", "deleteTemplateFailed",params);

			}else if ( engine.getSource() != Engine.ENGINE_SOURCE_LOCAL ){

				Map<String, Object> params = new HashMap<>();
				params.put( "id", new Long( id ));
				params.put( "error", "Template is not local" );

				sendBrowserMessage("metasearch", "deleteTemplateFailed",params);

			}else{

				engine.delete();

				Map<String, Object> params = new HashMap<>();
				params.put( "id", new Long( id ));
				sendBrowserMessage( "metasearch", "deleteTemplateCompleted", params );
			}
		} else if( OP_TEST_TEMPLATE.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			final long	id		= ((Long)decodedMap.get( "id" )).longValue();
			long	match_count	= ((Long)decodedMap.get( "max_matches" )).longValue();

			String searchText 	= (String) decodedMap.get("searchText");
			String headers		= (String) decodedMap.get("headers");

			final Long	sid = (Long)decodedMap.get( "sid" );

			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );

			if ( engine == null ){

				Map<String, Object> params = new HashMap<>();
				params.put( "id", new Long( id ));
				params.put( "error", "Template not found" );
				if ( sid != null )params.put( "sid", sid );

				sendBrowserMessage("metasearch", "testTemplateFailed",params);

			}else{

				SearchParameter parameter = new SearchParameter("s",searchText);
				SearchParameter[] parameters = new SearchParameter[] {parameter};

				try{

					engine.search(
							parameters,
							new HashMap(),
							(int)match_count,
							(int)match_count,
							headers,
							new ResultListener()
							{
								private String	content;
								private List	matches = new ArrayList();

								@Override
								public void
								contentReceived(
									Engine 		engine,
									String 		_content )
								{
									content = _content;
								}

								@Override
								public void
								matchFound(
									Engine 		engine,
									String[] 	fields)
								{
									matches.add( fields );
								}

								@Override
								public void
								resultsReceived(
									Engine 		engine,
									Result[] 	results )
								{
								}

								@Override
								public void
								resultsComplete(
									Engine 		engine )
								{
									Map<String, Object> params = new HashMap<>();
									params.put( "id", new Long( id ));
									if ( sid != null )params.put( "sid", sid );
									params.put( "content", JSONObject.escape( content ));

									JSONArray	l_matches = new JSONArray();

									params.put( "matches", l_matches );

									for (int i=0;i<matches.size();i++){

										String[]	match = (String[])matches.get(i);

										JSONArray	l_match = new JSONArray();

										l_matches.add( l_match );

										Collections.addAll(l_match, match);
									}

									sendBrowserMessage( "metasearch", "testTemplateCompleted", params );

								}

								@Override
								public void
								engineFailed(
									Engine 		engine,
									Throwable 	e )
								{
									Debug.out( e );

									Map<String, Object> params = new HashMap<>();
									params.put( "id", new Long( id ));
									params.put( "error", Debug.getNestedExceptionMessage( e ));
									if ( sid != null )params.put( "sid", sid );

									sendBrowserMessage("metasearch", "testTemplateFailed",params);
								}

								@Override
								public void
								engineRequiresLogin(
									Engine 		engine,
									Throwable 	e )
								{
									Map<String, Object> params = new HashMap<>();
									params.put( "id", new Long( id ));
									params.put( "error", Debug.getNestedExceptionMessage( e ));
									if ( sid != null )params.put( "sid", sid );

									sendBrowserMessage("metasearch", "testTemplateRequiresLogin",params);
								}
							});
				}catch( SearchException e ){
						// listener handles
				}
			}
		} else if ( OP_EXPORT_TEMPLATE.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			final long	id		= ((Long)decodedMap.get( "id" )).longValue();

			final Engine engine = metaSearchManager.getMetaSearch().getEngine( id );

			if ( engine == null ){

				Map<String, Object> params = new HashMap<>();
				params.put( "error", "template '" + id + "' not found" );

				sendBrowserMessage("metasearch", "exportTemplateFailed",params);

			}else{
				final Shell shell = Utils.findAnyShell();

				shell.getDisplay().asyncExec(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							FileDialog dialog =
								new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.SAVE );

							dialog.setFilterPath( TorrentOpener.getFilterPathData() );

							dialog.setText(MessageText.getString("metasearch.export.select.template.file"));

							dialog.setFilterExtensions( VuzeFileHandler.getVuzeFileFilterExtensions());

							dialog.setFilterNames( VuzeFileHandler.getVuzeFileFilterExtensions());

							String path = TorrentOpener.setFilterPathData( dialog.open());

							if ( path != null ){

								if ( !VuzeFileHandler.isAcceptedVuzeFileName( path )){

									path = VuzeFileHandler.getVuzeFileName( path );
								}

								try{
									engine.exportToVuzeFile( new File( path ));

									Map<String, Object> params = new HashMap<>();
									params.put( "id", new Long( id ));
									sendBrowserMessage( "metasearch", "exportTemplateCompleted", params );

								}catch( Throwable e ){

									Debug.out( e );

									Map<String, Object> params = new HashMap<>();
									params.put( "id", new Long( id ));
									params.put( "error", "save failed: " + Debug.getNestedExceptionMessage(e));

									sendBrowserMessage("metasearch", "exportTemplateFailed",params);
								}
							}else{

								Map<String, Object> params = new HashMap<>();
								params.put( "id", new Long( id ));
								params.put( "error", "operation cancelled" );

								sendBrowserMessage("metasearch", "exportTemplateFailed",params);
							}
						}
					});
			}
		}else if ( OP_IMPORT_TEMPLATE.equals(opid)){

			final Shell shell = Utils.findAnyShell();

			shell.getDisplay().asyncExec(
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						FileDialog dialog =
							new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.OPEN );

						dialog.setFilterPath( TorrentOpener.getFilterPathData() );

						dialog.setText(MessageText.getString("metasearch.import.select.template.file"));

						dialog.setFilterExtensions( VuzeFileHandler.getVuzeFileFilterExtensions());

						dialog.setFilterNames( VuzeFileHandler.getVuzeFileFilterExtensions());

						String path = TorrentOpener.setFilterPathData( dialog.open());

						if ( path != null ){

							VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

							VuzeFile vf = vfh.loadAndHandleVuzeFile( path, VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE );

							if ( vf == null ){

								Map<String, Object> params = new HashMap<>();
								params.put( "error", "invalid .biglybt file" );

								sendBrowserMessage("metasearch", "importTemplateFailed",params);

							}else{

								VuzeFileComponent[] comps = vf.getComponents();

								for (int i=0;i<comps.length;i++){

									VuzeFileComponent comp = comps[i];

									if ( comp.getType() == VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE ){

										Engine engine = (Engine)comp.getData( Engine.VUZE_FILE_COMPONENT_ENGINE_KEY );

										if ( engine != null ){

											Map<String, Object> params = new HashMap<>();
											params.put( "id", new Long( engine.getId()));
											sendBrowserMessage( "metasearch", "importTemplateCompleted", params );

											return;
										}
									}
								}

								Map<String, Object> params = new HashMap<>();
								params.put( "error", "invalid search template file" );

								sendBrowserMessage("metasearch", "importTemplateFailed",params);
							}
						}else{

							Map<String, Object> params = new HashMap<>();
								// don't change this message as the UI uses it!
							params.put( "error", "operation cancelled" );

							sendBrowserMessage("metasearch", "importTemplateFailed",params);
						}
					}
				});
		}else if ( OP_OPEN_SEARCH_RESULTS.equals(opid)){

			Map decodedMap = message.getDecodedMap();
			openCloseSearchDetailsListener.openSearchResults(decodedMap);
		}else if ( OP_CLOSE_SEARCH_RESULTS.equals(opid)){

			Map decodedMap = message.getDecodedMap();
			openCloseSearchDetailsListener.closeSearchResults(decodedMap);
		}else if(OP_LOAD_TORRENT.equals(opid)) {
			Map decodedMap = message.getDecodedMap();

			String torrentUrl		= (String) decodedMap.get( "torrent_url" );
			String referer_str	= (String) decodedMap.get( "referer_url" );

			try {

				Map headers = UrlUtils.getBrowserHeaders( referer_str );

				String subscriptionId		= ((String)decodedMap.get("subs_id"));
				String subscriptionResultId = ((String)decodedMap.get("subs_rid"));

				if ( subscriptionId != null ){

					Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( subscriptionId );

					if ( subs != null ){

						try{
							Engine engine = subs.getEngine();

							if ( engine != null && engine instanceof WebEngine ){

								WebEngine webEngine = (WebEngine) engine;

								if ( webEngine.isNeedsAuth()){

									headers.put( "Cookie",webEngine.getCookies());
								}
							}
						}catch( Throwable e ){

							Debug.out( e );
						}

						if ( subscriptionResultId != null ){

							subs.addPotentialAssociation( subscriptionResultId, torrentUrl );
						}
					}
				}else{
					try{
						long engineID		= ((Long)decodedMap.get("engine_id")).longValue();

						Engine engine = metaSearchManager.getMetaSearch().getEngine( engineID );

						if ( engine != null ){

							engine.addPotentialAssociation( torrentUrl );
						}

						if ( engine != null && engine instanceof WebEngine ){

							WebEngine webEngine = (WebEngine) engine;

							if ( webEngine.isNeedsAuth()){

								headers.put( "Cookie",webEngine.getCookies());
							}
						}
					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}

				try{
					String hash = (String)decodedMap.get( "hash" );

					if ( hash != null ){

						if ( !torrentUrl.toLowerCase( Locale.US ).startsWith( "magnet:" )){

							headers.put( "X-Alternative-URI-1", UrlUtils.getMagnetURI( Base32.decode( hash )));
						}
					}
				}catch( Throwable e ){
				}

				PluginInitializer.getDefaultInterface().getDownloadManager().addDownload(
						new URL(torrentUrl),
						headers );

				Map<String, Object> params = new HashMap<>();
				params.put("torrent_url",torrentUrl);
				params.put("referer_url",referer_str);
				sendBrowserMessage("metasearch", "loadTorrentCompleted",params);

			} catch(Exception e) {
				Map<String, Object> params = new HashMap<>();
				params.put("torrent_url",torrentUrl);
				params.put("referer_url",referer_str);
				params.put( "error", e.getMessage() );
				sendBrowserMessage("metasearch", "loadTorrentFailed",params);
			}

		}else if(OP_HAS_LOAD_TORRENT.equals(opid)) {

			Map<String, Object> params = new HashMap<>();
			params.put("result","1");
			sendBrowserMessage("metasearch", "hasLoadTorrent",params);

		}else if(OP_CREATE_SUBSCRIPTION.equals(opid)) {

			Map decodedMap = message.getDecodedMap();

			Long	 tid = (Long) decodedMap.get("tid");

			String 		name 		= (String) decodedMap.get( "name" );
			Boolean 	isPublic	= (Boolean) decodedMap.get( "is_public" );
			Map			options		= (Map)decodedMap.get( "options" );

			Boolean 	isEnabled		= (Boolean)options.get( "is_enabled" );
			Boolean 	autoDownload	= (Boolean)options.get( "auto_dl" );

			Map<String, Object> result = new HashMap<>();

			if ( tid != null )result.put( "tid", tid );

			try{
				JSONObject	payload = new JSONObject();

					// change this you need to change update too below

				payload.put( "engine_id", decodedMap.get( "engine_id" ));
				payload.put( "search_term", decodedMap.get( "search_term" ));
				payload.put( "filters", decodedMap.get( "filters" ));
				payload.put( "schedule", decodedMap.get( "schedule" ));
				payload.put( "options", decodedMap.get( "options" ));

				Subscription subs = SubscriptionManagerFactory.getSingleton().create(name, isPublic.booleanValue(), payload.toString());

				subs.getHistory().setDetails(
					isEnabled==null?true:isEnabled.booleanValue(),
					autoDownload==null?false:autoDownload.booleanValue());

				result.put( "id", subs.getID());

				subs.requestAttention();

				sendBrowserMessage( "metasearch", "createSubscriptionCompleted", result );

			} catch( Throwable e ){

				Debug.out( e );

				result.put( "error", "create failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage( "metasearch", "createSubscriptionFailed", result );
			}
		}else if( OP_READ_SUBSCRIPTION.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			final Long	 tid = (Long) decodedMap.get("tid");

			final String sid = (String) decodedMap.get("id");

			Map<String, Object> result = new HashMap<>();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );

				if ( subs == null ){

					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "readSubscriptionFailed",result);

				}else{

					boolean	shareable = subs.isShareable();

						// override public flag if not shareable

					result.put( "id", subs.getID());
					result.put( "name", subs.getName());
					result.put( "is_public", Boolean.valueOf(shareable && subs.isPublic()));
					result.put( "is_author", Boolean.valueOf(subs.isMine()));
					result.put( "is_shareable", Boolean.valueOf(shareable));
					result.put( "auto_dl_supported", Boolean.valueOf(subs.isAutoDownloadSupported()));

					SubscriptionHistory history = subs.getHistory();

					Map<String, Object>	options = new HashMap<>();

					result.put( "options", options );

					options.put( "is_enabled", Boolean.valueOf(history.isEnabled()));
					options.put( "auto_dl", Boolean.valueOf(history.isAutoDownload()));

					Map<String, Object>	info = new HashMap<>();

					result.put( "info", info );

					info.put( "last_scan", new Long( history.getLastScanTime()));
					info.put( "last_new", new Long( history.getLastNewResultTime()));
					info.put( "num_unread", new Long( history.getNumUnread()));
					info.put( "num_read", new Long( history.getNumRead()));

					String json = subs.getJSON();

					Map map = JSONUtils.decodeJSON( json );

					result.put( "engine_id", map.get( "engine_id" ));
					result.put( "search_term", map.get( "search_term" ));
					result.put( "filters", map.get( "filters" ));
					result.put( "schedule", map.get( "schedule" ));

					sendBrowserMessage( "metasearch", "readSubscriptionCompleted", result );
				}
			} catch( Throwable e ){

				Debug.out( e );

				result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "readSubscriptionFailed",result);
			}
		}else if (OP_UPDATE_SUBSCRIPTION.equals(opid)) {

			Map decodedMap = message.getDecodedMap();

			final Long	 tid = (Long) decodedMap.get("tid");

			final String 	name 		= (String)decodedMap.get("name");
			final Boolean 	isPublic	= (Boolean)decodedMap.get( "is_public" );
			final String 	sid 		= (String)decodedMap.get("id");

			Map			options		= (Map)decodedMap.get( "options" );

			Boolean 	isEnabled		= (Boolean)options.get( "is_enabled" );
			Boolean 	autoDownload	= (Boolean)options.get( "auto_dl" );

			Map<String, Object> result = new HashMap<>();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );

				if ( subs == null ){

					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "updateSubscriptionFailed",result);

				}else{

					JSONObject	payload = new JSONObject();

						// change this you need to change create too above

					payload.put( "engine_id", decodedMap.get( "engine_id" ));
					payload.put( "search_term", decodedMap.get( "search_term" ));
					payload.put( "filters", decodedMap.get( "filters" ));
					payload.put( "schedule", decodedMap.get( "schedule" ));
					payload.put( "options", decodedMap.get( "options" ));

					boolean	changed = subs.setDetails( name, isPublic.booleanValue(), payload.toString());

					subs.getHistory().setDetails(
							isEnabled==null?true:isEnabled.booleanValue(),
							autoDownload==null?false:autoDownload.booleanValue());

					if ( changed ){

						subs.reset();

						try{
							subs.getManager().getScheduler().downloadAsync(subs, true);

						}catch( Throwable e ){

							Debug.out(e);
						}
					}

					result.put( "id", subs.getID());

					sendBrowserMessage( "metasearch", "updateSubscriptionCompleted", result );
				}
			} catch( Throwable e ){

				Debug.out( e );

				result.put( "error", "update failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "updateSubscriptionFailed",result);
			}

		}else if (OP_SUBSCRIPTION_SET_AUTODL.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			String 	sid = (String)decodedMap.get("id");

			Long	tid = (Long) decodedMap.get("tid");

			Boolean 	autoDownload	= (Boolean)decodedMap.get( "auto_dl" );

			Map<String, Object> result = new HashMap<>();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );

				if ( subs == null ){

					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "setSubscriptionAutoDownloadFailed",result);

				}else{

					subs.getHistory().setAutoDownload( autoDownload.booleanValue());

					sendBrowserMessage( "metasearch", "setSubscriptionAutoDownloadCompleted", result );
				}
			} catch( Throwable e ){

				Debug.out( e );

				result.put( "error", "update failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "setSubscriptionAutoDownloadFailed",result);
			}

		}else if(OP_READ_SUBSCRIPTION_RESULTS.equals(opid)) {

			Map decodedMap = message.getDecodedMap();

			final Long	 tid = (Long) decodedMap.get("tid");

			final String sid = (String) decodedMap.get("id");

			final Map<String, Object> result = new HashMap<>();

			if ( tid != null )result.put( "tid", tid );

			try{
				final Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );

				if ( subs == null ){

					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "readSubscriptionResultsFailed",result);

				}else{

					result.put( "id", subs.getID());

					if ( !handleSubscriptionAuth( subs, result )){

						if ( subs.getHistory().getLastScanTime() == 0 ){

							subs.getManager().getScheduler().download(
									subs,
									false,
									new SubscriptionDownloadListener()
									{
										@Override
										public void
										complete(
											Subscription		subs )
										{
											if ( !handleSubscriptionAuth( subs, result )){

												encodeResults( subs, result );

												sendBrowserMessage( "metasearch", "readSubscriptionResultsCompleted", result );

												openCloseSearchDetailsListener.resizeMainBrowser();

											}
										}

										@Override
										public void
										failed(
											Subscription			subs,
											SubscriptionException	error )
										{
											Debug.out( error );

											result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(error));

											sendBrowserMessage( "metasearch", "readSubscriptionResultsFailed", result );

										}
									});
						}else{

							encodeResults( subs, result );

							sendBrowserMessage( "metasearch", "readSubscriptionResultsCompleted", result );

							openCloseSearchDetailsListener.resizeMainBrowser();
						}
					}
				}
			}catch( Throwable e ){

				Debug.out( e );

				result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "readSubscriptionFailed",result);
			}
		}else if( OP_DELETE_SUBSCRIPTION_RESULTS.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			String sid = (String)decodedMap.get("id");

			List	rids	= (List)decodedMap.get( "rids" );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );

				if ( subs == null ){

					Map<String, Object> params = new HashMap<>();

					params.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "deleteSubscriptionResultsFailed",params);

				}else{

					String[]	rids_a = (String[])rids.toArray( new String[rids.size()]);

					subs.getHistory().deleteResults( rids_a );

					Map<String, Object> result = new HashMap<>();

					result.put( "rids", rids);

					sendBrowserMessage( "metasearch", "deleteSubscriptionResultsCompleted", result );
				}
			} catch( Throwable e ){

				Debug.out( e );

				Map<String, Object> params = new HashMap<>();

				params.put( "error", "delete failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "deleteSubscriptionResultsFailed",params);
			}
		}else if( OP_MARK_SUBSCRIPTION_RESULTS.equals(opid)){

			Map decodedMap = message.getDecodedMap();

			String sid = (String)decodedMap.get("id");

			List	rids	= (List)decodedMap.get( "rids" );
			List	reads	= (List)decodedMap.get( "reads" );

			Map<String, Object> result = new HashMap<>();

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );

				if ( subs == null ){

					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "markSubscriptionResultsFailed",result);

				}else{

					String[]	rids_a = (String[])rids.toArray( new String[rids.size()]);

					boolean[]	reads_a = new boolean[reads.size()];

					for (int i=0;i<reads.size();i++){

						reads_a[i] = ((Boolean)reads.get(i)).booleanValue();
					}

					subs.getHistory().markResults( rids_a, reads_a );

					result.put( "rids", rids);
					result.put( "reads", reads);

					sendBrowserMessage( "metasearch", "markSubscriptionResultsCompleted", result );
				}
			} catch( Throwable e ){

				Debug.out( e );

				result.put( "error", "mark failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "markSubscriptionResultsFailed",result);
			}
		}else if( OP_DOWNLOAD_SUBSCRIPTION.equals(opid)) {

			Map decodedMap = message.getDecodedMap();

			final Long	 tid = (Long) decodedMap.get("tid");

			final String sid = (String) decodedMap.get("id");

			final Map<String, Object> result = new HashMap<>();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );

				if ( subs == null ){

					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "downloadSubscriptionFailed", result );

				}else{

					result.put( "id", subs.getID());

					if ( !handleSubscriptionAuth( subs, result )){

						subs.getManager().getScheduler().download(
							subs,
							false,
							new SubscriptionDownloadListener()
							{
								@Override
								public void
								complete(
									Subscription		subs )
								{
									if ( !handleSubscriptionAuth( subs, result )){

										encodeResults( subs, result );

										sendBrowserMessage( "metasearch", "downloadSubscriptionCompleted", result );
									}
								}

								@Override
								public void
								failed(
									Subscription			subs,
									SubscriptionException	error )
								{
									Debug.out( error );

									result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(error));

									sendBrowserMessage( "metasearch", "downloadSubscriptionFailed", result );

								}
							});
					}
				}
			} catch( Throwable e ){

				Debug.out( e );

				result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage( "metasearch", "downloadSubscriptionFailed", result );
			}
		}else if( OP_IS_CUSTOMISED.equals(opid)) {

			boolean custom = CustomizationManagerFactory.getSingleton().getActiveCustomization() != null;

			Map<String, Object> params = new HashMap<>();

			params.put( "is_custom", Boolean.valueOf(custom));

			sendBrowserMessage( "metasearch", "isCustomizedResult", params );
		}
	}

	protected boolean
	handleSubscriptionAuth(
		final Subscription		subs,
		final Map				result )
	{
		if ( subs.getHistory().isAuthFail()){

			try{
				Engine engine = subs.getEngine();

				if ( engine instanceof WebEngine ){

					final WebEngine webEngine = (WebEngine)engine;

					synchronized( active_subs_auth ){

						if ( active_subs_auth.contains( subs )){

							return( false );
						}

						active_subs_auth.add( subs );
					}

					Utils.execSWTThread( new Runnable() {
						@Override
						public void run() {
							new ExternalLoginWindow(
								new ExternalLoginListener()
								{
									private String previous_cookies;

									private boolean	result_sent;

									@Override
									public void
									canceled(
										ExternalLoginWindow 	window )
									{
										try{
											encodeResults( subs, result );

											sendBrowserMessage( "metasearch", "readSubscriptionResultsCompleted", result );

										}finally{

											completed();
										}
									}

									@Override
									public void
									cookiesFound(
										ExternalLoginWindow 	window,
										String 					cookies )
									{
										if ( handleCookies( cookies, false )){

											window.close();
										}
									}

									@Override
									public void
									done(
										ExternalLoginWindow 	window,
										String 					cookies )
									{
										try{
											if ( !handleCookies( cookies, true )){

												encodeResults( subs, result );

												sendBrowserMessage( "metasearch", "readSubscriptionResultsCompleted", result );
											}
										}finally{

											completed();
										}
									}

									private void
									completed()
									{
										synchronized( active_subs_auth ){

											active_subs_auth.remove( subs );
										}
									}

									private boolean
									handleCookies(
										String		cookies,
										boolean		force_if_ready )
									{
										if ( result_sent ){

											return( false );
										}

										String[] required = webEngine.getRequiredCookies();

										boolean	skip = required.length == 0 && !force_if_ready;

										if ( CookieParser.cookiesContain( required, cookies )){

											webEngine.setCookies(cookies);

											if ( previous_cookies == null || !previous_cookies.equals( cookies )){

												previous_cookies = cookies;

												if ( !skip ){

														// search operation will report outcome

													result_sent	= true;

													try{
														subs.getManager().getScheduler().download(
															subs,
															false,
															new SubscriptionDownloadListener()
															{
																@Override
																public void
																complete(
																	Subscription		subs )
																{
																	result.put( "id", subs.getID());

																	encodeResults( subs, result );

																	sendBrowserMessage( "metasearch", "readSubscriptionResultsCompleted", result );
																}

																@Override
																public void
																failed(
																	Subscription			subs,
																	SubscriptionException	error )
																{
																	Debug.out( error );

																	result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(error));

																	sendBrowserMessage( "metasearch", "readSubscriptionResultsFailed", result );
																}
															});
													}catch( Throwable error ){

														Debug.out( error );

														result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(error));

														sendBrowserMessage( "metasearch", "readSubscriptionResultsFailed", result );
													}
												}
											}
										}

										return( result_sent );
									}
								},
							webEngine.getName(),
							webEngine.getLoginPageUrl(),
							false,
							webEngine.getAuthMethod(),
							subs.isMine());
						}
					});

					return( true );

				}else{

					return( false );
				}
			}catch( Throwable e ){

				Debug.printStackTrace(e);

				return( false );
			}
		}else{

			return( false );
		}
	}

	protected void
	search(
		Map		decodedMap,
		Engine	target )
	{
		String searchText = (String) decodedMap.get("searchText");

		String headers = (String)decodedMap.get( "headers" );

		final Long	sid = (Long)decodedMap.get( "sid" );

		Boolean	mature = (Boolean)decodedMap.get( "mature" );

		Long	l_max_per_engine = (Long)decodedMap.get( "maxResultsPerEngine" );

		int	max_per_engine = l_max_per_engine==null?100:l_max_per_engine.intValue();

		if ( max_per_engine < 1 ){

			max_per_engine = 1;
		}

		if ( target == null ){

				// override engine selection for subscriptions

			String subscriptionId		= ((String)decodedMap.get("subs_id"));

			if ( subscriptionId != null ){

				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( subscriptionId );

				if ( subs != null ){

					try{
						Engine engine = subs.getEngine();

						if ( engine != null ){

							target = engine;
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}

		ResultListener listener = new ResultListener() {

			@Override
			public void
			contentReceived(
				Engine engine,
				String content )
			{
			}

			@Override
			public void
			matchFound(
				Engine 		engine,
				String[] 	fields )
			{
			}

			@Override
			public void engineFailed(Engine engine, Throwable e) {

				Debug.out( e );

				Map params = getParams( engine );

				params.put( "error", Debug.getNestedExceptionMessage( e ));

				sendBrowserMessage("metasearch", "engineFailed", params );
			}

			@Override
			public void engineRequiresLogin(Engine engine, Throwable e) {
				Map params = getParams( engine );

				params.put( "error", Debug.getNestedExceptionMessage( e ));

				sendBrowserMessage("metasearch", "engineRequiresLogin", params );
			}

			@Override
			public void resultsComplete(Engine engine) {

				sendBrowserMessage("metasearch", "engineCompleted", getParams( engine ));
			}

			@Override
			public void resultsReceived(Engine engine, Result[] results) {
				Map params = getParams(engine);
				List resultsList = new ArrayList(results.length);
				for(int i = 0 ; i < results.length ; i++) {
					Result result = results[i];
					resultsList.add(result.toJSONMap());
				}
				params.put("results", resultsList);
				sendBrowserMessage("metasearch", "resultsReceived",params);
			}

			protected Map
			getParams(
				Engine	engine )
			{
				Map<String, Object> params = new HashMap<>();
				params.put("id", new Long(engine.getId()));
				params.put("name", engine.getName());
				params.put("favicon", engine.getIcon());
				params.put("dl_link_css", engine.getDownloadLinkCSS());
				params.put("shareable", Boolean.valueOf(engine.isShareable()));

				if ( sid != null ){
					params.put( "sid", sid );
				}
				return( params );
			}
		};

		List<SearchParameter>	sps = new ArrayList<>();

		sps.add( new SearchParameter( "s", searchText ));

		if ( mature != null ){

			sps.add( new SearchParameter( "m", mature.toString()));
		}

		SearchParameter[] parameters = (SearchParameter[])sps.toArray(new SearchParameter[ sps.size()] );

		MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();

		Map<String,String>	context = new HashMap<>();

		context.put( Engine.SC_FORCE_FULL, "true" );

		context.put( Engine.SC_BATCH_PERIOD, "1000" );

		context.put( Engine.SC_REMOVE_DUP_HASH, "true" );

		if ( target == null ){

			metaSearchManager.getMetaSearch().search( listener, parameters, headers, context, max_per_engine );

		}else{

			metaSearchManager.getMetaSearch().search( new Engine[]{ target }, listener, parameters, headers, context, max_per_engine );

		}
	}

	protected void
	encodeResults(
		Subscription	subs,
		Map				result )
	{
		JSONArray	results_list = new JSONArray();

		SubscriptionResult[]	results = subs.getHistory().getResults( false );

		for(int i=0; i<results.length; i++){

			SubscriptionResult r = results[i];

			results_list.add( r.toJSONMap());
		}

		result.put( "results", results_list );


	}

	protected Engine
	getEngineFromId(
			long id )
	{
		MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();

		Engine[] engines = metaSearchManager.getMetaSearch().getEngines( false, true );
		for(int i = 0 ; i < engines.length ; i++) {
			Engine engine = engines[i];
			if(engine.getId() == id) {
				return engine;
			}
		}
		return null;
	}

	public boolean
	sendBrowserMessage(
		String 		key,
		String 		op,
		Map 		params )
	{
		MetaSearchManagerFactory.getSingleton().log( "BrowserListener: sent " + op + ": " + params );

		return( context.sendBrowserMessage(key, op, params));
	}

	public boolean
	sendBrowserMessage(
		String 			key,
		String 			op,
		Collection 		params )
	{
		MetaSearchManagerFactory.getSingleton().log( "BrowserListener: sent " + op + ": " + params );

		return( context.sendBrowserMessage(key, op, params));
	}

}
