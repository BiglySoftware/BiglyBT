/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package com.biglybt.ui.swt.views.clientstats;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.ui.common.table.TableView;
import com.biglybt.ui.common.table.TableViewFilterCheck;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerListener;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.util.*;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnCreationListener;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.BubbleTextBox;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;

import com.biglybt.core.peermanager.peerdb.PeerItem;
import com.biglybt.core.peermanager.piecepicker.util.BitFlags;
import com.biglybt.core.util.bloom.BloomFilter;
import com.biglybt.core.util.bloom.BloomFilterFactory;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableLifeCycleListener;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.util.MapUtils;

/**
 * This view needs to be reworked so that the data collection is separate
 * from UI
 */
public class ClientStatsView
	extends TableViewTab<ClientStatsDataSource>
	implements TableLifeCycleListener, GlobalManagerListener,
	DownloadManagerPeerListener, TableViewFilterCheck<ClientStatsDataSource>
{
	private static final String CONFIG_FILE = "ClientStats.dat";

	private static final String CONFIG_FILE_ARCHIVE = "ClientStats_%1.dat";

	private static final int BLOOMFILTER_SIZE = 100000;

	private static final int BLOOMFILTER_PEERID_SIZE = 50000;

	private static final String TABLEID = "ClientStats";

	private Core core;

	private TableViewSWT<ClientStatsDataSource> tv;

	private boolean columnsAdded;

	private final Map<String, ClientStatsDataSource> mapData = new HashMap<>();

	private Composite parent;

	private BloomFilter bloomFilter;

	private BloomFilter bloomFilterPeerId;

	private ClientStatsOverall overall;

	private long startedListeningOn;

	private long totalTime;

	private long lastAdd;

	private GregorianCalendar calendar = new GregorianCalendar();

	private int lastAddMonth;

	private static boolean registered = false;

	public ClientStatsView() {
		super("ClientStats");

		initAndLoad();

		CoreFactory.addCoreRunningListener(new CoreRunningListener() {
			@Override
			public void coreRunning(Core core) {
				initColumns(core);
				register(core);
			}
		});
	}

	@Override
	public Composite initComposite(Composite composite) {
		Composite parent = new Composite(composite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		parent.setLayout(layout);

		Layout compositeLayout = composite.getLayout();
		if (compositeLayout instanceof GridLayout) {
			parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		} else if (compositeLayout instanceof FormLayout) {
			parent.setLayoutData(Utils.getFilledFormData());
		}
		
		Composite cTop = new Composite(parent, SWT.NONE);

		cTop.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		cTop.setLayout(new FormLayout());

		
		Button btnCopy = new Button(cTop, SWT.PUSH);
		Messages.setLanguageText( btnCopy, "label.copy" );
		btnCopy.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableRowCore[] rows = tv.getRows();
				StringBuilder sb = new StringBuilder();

				sb.append(new SimpleDateFormat("MMM yyyy").format(new Date()));
				sb.append("\n");

				sb.append("Hits,Client,Bytes Sent,Bytes Received,Bad Bytes\n");
				for (TableRowCore row : rows) {
					ClientStatsDataSource stat = (ClientStatsDataSource) row.getDataSource();
					if (stat == null) {
						continue;
					}
					sb.append(stat.count);
					sb.append(",");
					sb.append(stat.client.replaceAll(",", ""));
					sb.append(",");
					sb.append(stat.bytesSent);
					sb.append(",");
					sb.append(stat.bytesReceived);
					sb.append(",");
					sb.append(stat.bytesDiscarded);
					sb.append("\n");
				}
				ClipboardCopy.copyToClipBoard(sb.toString());
			}
		});

		Button btnCopyShort = new Button(cTop, SWT.PUSH);
		btnCopyShort.setText( MessageText.getString( "label.copy" ) + " > 1%");
		btnCopyShort.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				StringBuilder sb = new StringBuilder();

				sb.append(new SimpleDateFormat("MMM ''yy").format(new Date()));
				sb.append("] ");
				sb.append(overall.count);
				sb.append(": ");

				ClientStatsDataSource[] stats;

				synchronized (mapData) {

					stats = mapData.values().toArray(new ClientStatsDataSource[0]);
				}

				Arrays.sort(stats, new Comparator<ClientStatsDataSource>() {
					@Override
					public int compare(ClientStatsDataSource o1, ClientStatsDataSource o2) {
						if (o1.count == o2.count) {
							return 0;
						}
						return o1.count > o2.count ? -1 : 1;
					}
				});

				boolean first = true;
				for (ClientStatsDataSource stat : stats) {
					int pct = (int) (stat.count * 1000 / overall.count);
					if (pct < 10) {
						continue;
					}
					if (first) {
						first = false;
					} else {
						sb.append(", ");
					}
					sb.append(DisplayFormatters.formatPercentFromThousands(pct));
					sb.append(" ");
					sb.append(stat.client);
				}

				Arrays.sort(stats, new Comparator<ClientStatsDataSource>() {
					@Override
					public int compare(ClientStatsDataSource o1, ClientStatsDataSource o2) {
						float v1 = (float) o1.bytesReceived / o1.count;
						float v2 = (float) o2.bytesReceived / o2.count;
						if (v1 == v2) {
							return 0;
						}
						return v1 > v2 ? -1 : 1;
					}
				});

				int top = 5;
				first = true;
				sb.append("\nBest Seeders (");
				long total = 0;
				for (ClientStatsDataSource stat : stats) {
					total += stat.bytesReceived;
				}
				sb.append(DisplayFormatters.formatByteCountToKiBEtc(total, false, true,
						0));
				sb.append(" Downloaded): ");
				for (ClientStatsDataSource stat : stats) {
					if (first) {
						first = false;
					} else {
						sb.append(", ");
					}
					sb.append(DisplayFormatters.formatByteCountToKiBEtc(
							stat.bytesReceived / stat.count, false, true, 0));
					sb.append(" per ");
					sb.append(stat.client);
					sb.append("(x");
					sb.append(stat.count);
					sb.append(")");
					if (--top <= 0) {
						break;
					}
				}

				Arrays.sort(stats, new Comparator<ClientStatsDataSource>() {
					@Override
					public int compare(ClientStatsDataSource o1, ClientStatsDataSource o2) {
						float v1 = (float) o1.bytesDiscarded / o1.count;
						float v2 = (float) o2.bytesDiscarded / o2.count;
						if (v1 == v2) {
							return 0;
						}
						return v1 > v2 ? -1 : 1;
					}
				});
				top = 5;
				first = true;
				sb.append("\nMost Discarded (");
				total = 0;
				for (ClientStatsDataSource stat : stats) {
					total += stat.bytesDiscarded;
				}
				sb.append(DisplayFormatters.formatByteCountToKiBEtc(total, false, true,
						0));
				sb.append(" Discarded): ");
				for (ClientStatsDataSource stat : stats) {
					if (first) {
						first = false;
					} else {
						sb.append(", ");
					}
					sb.append(DisplayFormatters.formatByteCountToKiBEtc(
							stat.bytesDiscarded / stat.count, false, true, 0));
					sb.append(" per ");
					sb.append(stat.client);
					sb.append("(x");
					sb.append(stat.count);
					sb.append(")");
					if (--top <= 0) {
						break;
					}
				}

				Arrays.sort(stats, new Comparator<ClientStatsDataSource>() {
					@Override
					public int compare(ClientStatsDataSource o1, ClientStatsDataSource o2) {
						float v1 = (float) o1.bytesSent / o1.count;
						float v2 = (float) o2.bytesSent / o2.count;
						if (v1 == v2) {
							return 0;
						}
						return v1 > v2 ? -1 : 1;
					}
				});
				top = 5;
				first = true;
				sb.append("\nMost Fed (");
				total = 0;
				for (ClientStatsDataSource stat : stats) {
					total += stat.bytesSent;
				}
				sb.append(DisplayFormatters.formatByteCountToKiBEtc(total, false, true,
						0));
				sb.append(" Sent): ");
				for (ClientStatsDataSource stat : stats) {
					if (first) {
						first = false;
					} else {
						sb.append(", ");
					}
					sb.append(DisplayFormatters.formatByteCountToKiBEtc(
							stat.bytesSent / stat.count, false, true, 0));
					sb.append(" per ");
					sb.append(stat.client);
					sb.append("(x");
					sb.append(stat.count);
					sb.append(")");
					if (--top <= 0) {
						break;
					}
				}

				ClipboardCopy.copyToClipBoard(sb.toString());
			}
		});
		
			
		
		BubbleTextBox bubbleTextBox = new BubbleTextBox(cTop, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);

		FormData fd = new FormData();
		btnCopy.setLayoutData( fd );

		fd = new FormData();
		fd.left = new FormAttachment(btnCopy, 5);
		btnCopyShort.setLayoutData(fd);
	
		
		fd = Utils.getFilledFormData();
		fd.width = 150;
		fd.top = null;
		fd.left = null;

		bubbleTextBox.setMessageAndLayout(MessageText.getString("Button.search") + "..." , fd);
		
		tv.enableFilterCheck(bubbleTextBox, this, true );

		Composite tableParent = new Composite(parent, SWT.NONE);

		tableParent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout gridLayout = new GridLayout();
		gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
		gridLayout.marginHeight = gridLayout.marginWidth = 0;
		tableParent.setLayout(gridLayout);

		parent.setTabList(new Control[] {tableParent, cTop});

		return tableParent;
	}

	@Override
	public void tableViewTabInitComplete() {
	}

	@Override
	public TableViewSWT<ClientStatsDataSource> initYourTableView() {
		tv = TableViewFactory.createTableViewSWT(ClientStatsDataSource.class,
				TABLEID, getPropertiesPrefix(), new TableColumnCore[0],
				ColumnCS_Count.COLUMN_ID, SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		/*
				tv.addTableDataSourceChangedListener(this, true);
				tv.addRefreshListener(this, true);
				tv.addSelectionListener(this, false);
				tv.addMenuFillListener(this);
				*/
		tv.addLifeCycleListener(this);

		return tv;
	}

	private void initColumns(Core core) {
		synchronized (ClientStatsView.class) {

			if (columnsAdded) {

				return;
			}

			columnsAdded = true;
		}

		UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();

		TableManager tableManager = uiManager.getTableManager();

		tableManager.registerColumn(ClientStatsDataSource.class,
				ColumnCS_Name.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnCS_Name(column);
					}
				});
		tableManager.registerColumn(ClientStatsDataSource.class,
				ColumnCS_Count.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnCS_Count(column);
					}
				});
		tableManager.registerColumn(ClientStatsDataSource.class,
				ColumnCS_Discarded.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnCS_Discarded(column);
					}
				});
		tableManager.registerColumn(ClientStatsDataSource.class,
				ColumnCS_Received.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnCS_Received(column);
					}
				});
		tableManager.registerColumn(ClientStatsDataSource.class,
				ColumnCS_ReceivedPer.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnCS_ReceivedPer(column);
					}
				});
		tableManager.registerColumn(ClientStatsDataSource.class,
				ColumnCS_Sent.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnCS_Sent(column);
					}
				});
		tableManager.registerColumn(ClientStatsDataSource.class,
				ColumnCS_Pct.COLUMN_ID, new TableColumnCreationListener() {
					@Override
					public void tableColumnCreated(TableColumn column) {
						new ColumnCS_Pct(column);
					}
				});

		for (final String network : AENetworkClassifier.AT_NETWORKS) {
			tableManager.registerColumn(ClientStatsDataSource.class, network + "."
					+ ColumnCS_Sent.COLUMN_ID, new TableColumnCreationListener() {
				@Override
				public void tableColumnCreated(TableColumn column) {
					column.setUserData("network", network);
					new ColumnCS_Sent(column);
				}
			});
			tableManager.registerColumn(ClientStatsDataSource.class, network + "."
					+ ColumnCS_Discarded.COLUMN_ID, new TableColumnCreationListener() {
				@Override
				public void tableColumnCreated(TableColumn column) {
					column.setUserData("network", network);
					new ColumnCS_Discarded(column);
				}
			});
			tableManager.registerColumn(ClientStatsDataSource.class, network + "."
					+ ColumnCS_Received.COLUMN_ID, new TableColumnCreationListener() {
				@Override
				public void tableColumnCreated(TableColumn column) {
					column.setUserData("network", network);
					new ColumnCS_Received(column);
				}
			});
			tableManager.registerColumn(ClientStatsDataSource.class, network + "."
					+ ColumnCS_Count.COLUMN_ID, new TableColumnCreationListener() {
				@Override
				public void tableColumnCreated(TableColumn column) {
					column.setUserData("network", network);
					new ColumnCS_Count(column);
				}
			});
		}

		TableColumnManager tcManager = TableColumnManager.getInstance();
		tcManager.setDefaultColumnNames(TABLEID, new String[] {
			ColumnCS_Name.COLUMN_ID,
			ColumnCS_Pct.COLUMN_ID,
			ColumnCS_Count.COLUMN_ID,
			ColumnCS_Received.COLUMN_ID,
			ColumnCS_Sent.COLUMN_ID,
			ColumnCS_Discarded.COLUMN_ID,
		});
	}

	private void initAndLoad() {
		synchronized (mapData) {
			Map map = FileUtil.readResilientConfigFile(CONFIG_FILE);

			totalTime = MapUtils.getMapLong(map, "time", 0);

			lastAdd = MapUtils.getMapLong(map, "lastadd", 0);
			if (lastAdd != 0) {
				calendar.setTimeInMillis(lastAdd);
				lastAddMonth = calendar.get(Calendar.MONTH);

				Map mapBloom = MapUtils.getMapMap(map, "bloomfilter", null);
				if (mapBloom != null) {
					bloomFilter = BloomFilterFactory.deserialiseFromMap(mapBloom);
				}
				mapBloom = MapUtils.getMapMap(map, "bloomfilterPeerId", null);
				if (mapBloom != null) {
					bloomFilterPeerId = BloomFilterFactory.deserialiseFromMap(mapBloom);
				}
			}
			if (bloomFilter == null) {
				bloomFilter = BloomFilterFactory.createRotating(
						BloomFilterFactory.createAddOnly(BLOOMFILTER_SIZE), 2);
			}
			if (bloomFilterPeerId == null) {
				bloomFilterPeerId = BloomFilterFactory.createRotating(
						BloomFilterFactory.createAddOnly(BLOOMFILTER_PEERID_SIZE), 2);
			}

			overall = new ClientStatsOverall();

			List listSavedData = MapUtils.getMapList(map, "data", null);
			if (listSavedData != null) {
				for (Object val : listSavedData) {
					try {
						Map mapVal = (Map) val;
						if (mapVal != null) {
							ClientStatsDataSource ds = new ClientStatsDataSource(mapVal);
							ds.overall = overall;

							if (!mapData.containsKey(ds.client)) {
								mapData.put(ds.client, ds);
								overall.count += ds.count;
							}
						}

					} catch (Exception e) {
						// ignore
					}
				}
			}
		}
	}

	
	@Override
	public boolean
	filterCheck(
		ClientStatsDataSource ds, String filter, boolean regex )
	{

		try {			
			String name = ds.client;

			String s = regex ? filter : RegExUtil.splitAndQuote( filter, "\\s*[|;]\\s*" );

			boolean	match_result = true;

			if ( regex && s.startsWith( "!" )){

				s = s.substring(1);

				match_result = false;
			}

			Pattern pattern = RegExUtil.getCachedPattern( "csv:search", s, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

			return( pattern.matcher(name).find() == match_result );

		} catch (Exception e) {

			return true;
		}
	}

	@Override
	public void filterSet(String filter)
	{
		// System.out.println( filter );
	}
	private void save(String filename) {
		Map<String, Object> map = new HashMap<>();
		synchronized (mapData) {
			map.put("data", new ArrayList(mapData.values()));
			map.put("bloomfilter", bloomFilter.serialiseToMap());
			map.put("bloomfilterPeerId", bloomFilterPeerId.serialiseToMap());
			map.put("lastadd", SystemTime.getCurrentTime());
			if (startedListeningOn > 0) {
				map.put("time", totalTime
						+ (SystemTime.getCurrentTime() - startedListeningOn));
			} else {
				map.put("time", totalTime);
			}
		}
		FileUtil.writeResilientConfigFile(filename, map);
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventype, Map<String, Object> data) {
		switch (eventype) {
			case TableLifeCycleListener.EVENT_TABLELIFECYCLE_INITIALIZED:
				synchronized (mapData) {
					if (mapData.values().size() > 0) {
						tv.addDataSources(mapData.values().toArray(new ClientStatsDataSource[0]));
					}
				}
				break;
		}
	}

	protected void register(Core core) {
		this.core = core;
		if (registered) {
			return;
		}
		core.getGlobalManager().addListener(this);
		synchronized (mapData) {
			startedListeningOn = SystemTime.getCurrentTime();
		}
		registered = true;
	}

	// @see com.biglybt.core.global.GlobalManagerListener#destroyInitiated()
	@Override
	public void destroyInitiated() {
		if (core == null) {
			return;
		}
		core.getGlobalManager().removeListener(this);
		List downloadManagers = core.getGlobalManager().getDownloadManagers();
		for (Object object : downloadManagers) {
			((DownloadManager) object).removePeerListener(this);
		}
		registered = false;
		save(CONFIG_FILE);
	}

	// @see com.biglybt.core.global.GlobalManagerListener#destroyed()
	@Override
	public void destroyed() {
	}

	@Override
	public void downloadManagerAdded(DownloadManager dm) {
		if (!dm.getDownloadState().getFlag(DownloadManagerState.FLAG_LOW_NOISE)) {
			dm.addPeerListener(this, true);
		}
	}

	@Override
	public void downloadManagerRemoved(DownloadManager dm) {
		dm.removePeerListener(this);
	}

	@Override
	public void seedingStatusChanged(boolean seedingOnlyMode,
	                                 boolean potentiallySeedingOnlyMode) {
	}

	@Override
	public void peerAdded(PEPeer peer) {
		peer.addListener(new PEPeerListener() {

			@Override
			public void stateChanged(PEPeer peer, int newState) {
				if (newState == PEPeer.TRANSFERING) {
					addPeer(peer);
				} else if (newState == PEPeer.CLOSING
						|| newState == PEPeer.DISCONNECTED) {
					peer.removeListener(this);
				}
			}

			@Override
			public void sentBadChunk(PEPeer peer, int pieceNum, int totalBadChunks) {
			}

			@Override
			public void removeAvailability(PEPeer peer, BitFlags peerHavePieces) {
			}

			@Override
			public void addAvailability(PEPeer peer, BitFlags peerHavePieces) {
			}
		});
	}

	protected void addPeer(PEPeer peer) {
		byte[] bloomId;
		long now = SystemTime.getCurrentTime();

		// Bloom Filter is based on the first 8 bytes of peer id + ip address
		// This captures more duplicates than peer id because most clients
		// randomize their peer id on restart.  IP address, however, changes
		// less often.
		byte[] address = null;
		byte[] peerId = peer.getId();
		InetAddress ip = peer.getAlternativeIPv6();
		if (ip == null) {
			try {
				ip = AddressUtils.getByName(peer.getIp());
				address = ip.getAddress();
			} catch (Throwable e) {
				String ipString = peer.getIp();
				if (ipString != null) {
					address = ByteFormatter.intToByteArray(ipString.hashCode());
				}
			}
		} else {
			address = ip.getAddress();
		}
		if (address == null) {
			bloomId = peerId;
		} else {
			bloomId = new byte[8 + address.length];
			System.arraycopy(peerId, 0, bloomId, 0, 8);
			System.arraycopy(address, 0, bloomId, 8, address.length);
		}

		synchronized (mapData) {
			// break on month.. assume user didn't last use this on the same month in a different year
			calendar.setTimeInMillis(now);
			int thisMonth = calendar.get(Calendar.MONTH);
			if (thisMonth != lastAddMonth) {
				if (lastAddMonth == 0) {
					lastAddMonth = thisMonth;
				} else {
					String s = new SimpleDateFormat("yyyy-MM").format(new Date(lastAdd));
					String filename = CONFIG_FILE_ARCHIVE.replace("%1", s);
					save(filename);

					lastAddMonth = thisMonth;
					lastAdd = 0;
					bloomFilter = BloomFilterFactory.createRotating(
							BloomFilterFactory.createAddOnly(BLOOMFILTER_SIZE), 2);
					bloomFilterPeerId = BloomFilterFactory.createRotating(
							BloomFilterFactory.createAddOnly(BLOOMFILTER_PEERID_SIZE), 2);
					overall = new ClientStatsOverall();
					mapData.clear();
					if (tv != null) {
						tv.removeAllTableRows();
					}
					totalTime = 0;
					startedListeningOn = 0;
				}
			}

			String id = getID(peer);
			ClientStatsDataSource stat;
			stat = mapData.get(id);
			boolean needNew = stat == null;
			if (needNew) {
				stat = new ClientStatsDataSource();
				stat.overall = overall;
				stat.client = id;
				mapData.put(id, stat);
			}

			boolean inBloomFilter = bloomFilter.contains(bloomId) || bloomFilterPeerId.contains(peerId);

			if (!inBloomFilter) {
				bloomFilter.add(bloomId);
				bloomFilterPeerId.add(peerId);

				lastAdd = now;
				synchronized (overall) {

					overall.count++;
				}
				stat.count++;
			}

			stat.current++;

			long existingBytesReceived = peer.getStats().getTotalDataBytesReceived();
			long existingBytesSent = peer.getStats().getTotalDataBytesSent();
			long existingBytesDiscarded = peer.getStats().getTotalBytesDiscarded();

			if (existingBytesReceived > 0) {
				stat.bytesReceived -= existingBytesReceived;
				if (stat.bytesReceived < 0) {
					stat.bytesReceived = 0;
				}
			}
			if (existingBytesSent > 0) {
				stat.bytesSent -= existingBytesSent;
				if (stat.bytesSent < 0) {
					stat.bytesSent = 0;
				}
			}
			if (existingBytesDiscarded > 0) {
				stat.bytesDiscarded -= existingBytesDiscarded;
				if (stat.bytesDiscarded < 0) {
					stat.bytesDiscarded = 0;
				}
			}

			if (peer instanceof PEPeerTransport) {
				PeerItem identity = ((PEPeerTransport) peer).getPeerItemIdentity();
				if (identity != null) {
					String network = identity.getNetwork();
					if (network != null) {
						Map<String, Object> map = stat.perNetworkStats.get(network);
						if (map == null) {
							map = new HashMap<>();
							stat.perNetworkStats.put(network, map);
						}
						if (!inBloomFilter) {
  						long count = MapUtils.getMapLong(map, "count", 0);
  						map.put("count", count + 1);
						}

						if (existingBytesReceived > 0) {
							long bytesReceived = MapUtils.getMapLong(map, "bytesReceived",
									0);
							bytesReceived -= existingBytesReceived;
							if (bytesReceived < 0) {
								bytesReceived = 0;
							}
							map.put("bytesReceived", bytesReceived);
						}
						if (existingBytesSent > 0) {
							long bytesSent = MapUtils.getMapLong(map, "bytesSent", 0);
							bytesSent -= existingBytesSent;
							if (bytesSent < 0) {
								bytesSent = 0;
							}
							map.put("bytesSent", bytesSent);
						}
						if (existingBytesDiscarded > 0) {
							long bytesDiscarded = MapUtils.getMapLong(map,
									"bytesDiscarded", 0);
							bytesDiscarded -= existingBytesDiscarded;
							if (bytesDiscarded < 0) {
								bytesDiscarded = 0;
							}
							map.put("bytesDiscarded", bytesDiscarded);
						}

					}
				}
			}

			if (tv != null) {
  			if (needNew) {
  				tv.addDataSource(stat);
  			} else {
  				TableRowCore row = tv.getRow(stat);
  				if (row != null) {
  					row.invalidate();
  				}
  			}
			}
		}
	}

	@Override
	public void peerManagerAdded(PEPeerManager manager) {
	}

	@Override
	public void peerManagerRemoved(PEPeerManager manager) {
	}

	@Override
	public void peerManagerWillBeAdded(PEPeerManager manager) {
	}

	@Override
	public void peerRemoved(PEPeer peer) {
		synchronized (mapData) {
			ClientStatsDataSource stat = mapData.get(getID(peer));
			if (stat != null) {
				stat.current--;

				String network = null;
				if (peer instanceof PEPeerTransport) {
					PeerItem identity = ((PEPeerTransport) peer).getPeerItemIdentity();
					if (identity != null) {
						network = identity.getNetwork();
					}
				}


				stat.bytesReceived += peer.getStats().getTotalDataBytesReceived();
				stat.bytesSent += peer.getStats().getTotalDataBytesSent();
				stat.bytesDiscarded += peer.getStats().getTotalBytesDiscarded();

				if (network != null) {
					Map<String, Object> map = stat.perNetworkStats.get(network);
					if (map == null) {
						map = new HashMap<>();
						stat.perNetworkStats.put(network, map);
					}
					long bytesReceived = MapUtils.getMapLong(map, "bytesReceived", 0);
					map.put("bytesReceived", bytesReceived
							+ peer.getStats().getTotalDataBytesReceived());
					long bytesSent = MapUtils.getMapLong(map, "bytesSent", 0);
					map.put("bytesSent", bytesSent
							+ peer.getStats().getTotalDataBytesSent());
					long bytesDiscarded = MapUtils.getMapLong(map, "bytesDiscarded", 0);
					map.put("bytesDiscarded", bytesDiscarded
							+ peer.getStats().getTotalBytesDiscarded());
				}

				if (tv != null) {
  				TableRowCore row = tv.getRow(stat);
  				if (row != null) {
  					row.invalidate();
  				}
				}
			}
		}
	}

	private String getID(PEPeer peer) {
		String s = peer.getClientNameFromPeerID();
		if (s == null) {
			s = peer.getClient();
			if (s.startsWith("HTTP Seed")) {
				return "HTTP Seed";
			}
		}
		return s.replaceAll(" v?[0-9._]+", "");
	}
}
