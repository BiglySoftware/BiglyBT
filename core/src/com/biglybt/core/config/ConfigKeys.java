/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.config;

public final class ConfigKeys {
	public static final String ICFG_USER_MODE = "User Mode";

	public static class BackupRestore {
		public static final String BCFG_BACKUP_AUTO_ENABLE = "br.backup.auto.enable";
		public static final String SCFG_BACKUP_AUTO_DIR = "br.backup.auto.dir";
		public static final String SCFG_BACKUP_FOLDER_DEFAULT = "br.backup.folder.default";
		public static final String ICFG_BACKUP_AUTO_EVERYDAYS = "br.backup.auto.everydays";
		public static final String ICFG_BACKUP_AUTO_EVERYHOURS = "br.backup.auto.everyhours";
		public static final String ICFG_BACKUP_AUTO_RETAIN = "br.backup.auto.retain";
		public static final String BCFG_BACKUP_NOTIFY = "br.backup.notify";
		public static final String BCFG_RESTORE_AUTOPAUSE = "br.restore.autopause";
		public static final String BCFG_BACKUP_PLUGINS = "br.restore.doplugins";
	}

	public static class Connection {
		public static final String ICFG_TCP_LISTEN_PORT = "TCP.Listen.Port";
		public static final String ICFG_UDP_LISTEN_PORT = "UDP.Listen.Port";
		public static final String ICFG_UDP_NON_DATA_LISTEN_PORT = "UDP.NonData.Listen.Port";
		public static final String ICFG_UDP_NON_DATA_LISTEN_PORT_SAME = "UDP.NonData.Listen.Port.Same";
		public static final String BCFG_TCP_LISTEN_PORT_ENABLE = "TCP.Listen.Port.Enable";
		public static final String BCFG_UDP_LISTEN_PORT_ENABLE = "UDP.Listen.Port.Enable";
		public static final String BCFG_LISTEN_PORT_RANDOMIZE_ENABLE = "Listen.Port.Randomize.Enable";
		public static final String SCFG_LISTEN_PORT_RANDOMIZE_RANGE = "Listen.Port.Randomize.Range";
		public static final String BCFG_LISTEN_PORT_RANDOMIZE_TOGETHER = "Listen.Port.Randomize.Together";
		public static final String BCFG_PEERCONTROL_TCP_PUBLIC_ENABLE = "peercontrol.tcp.public.enable";
		public static final String BCFG_PEERCONTROL_UDP_PUBLIC_ENABLE = "peercontrol.udp.public.enable";
		public static final String BCFG_PEERCONTROL_PREFER_UDP = "peercontrol.prefer.udp";
		public static final String BCFG_PEERCONTROL_PREFER_IPV6_CONNECTIONS = "peercontrol.prefer.ipv6";
		public static final String BCFG_HTTP_DATA_LISTEN_PORT_ENABLE = "HTTP.Data.Listen.Port.Enable";
		public static final String ICFG_HTTP_DATA_LISTEN_PORT = "HTTP.Data.Listen.Port";
		public static final String ICFG_HTTP_DATA_LISTEN_PORT_OVERRIDE = "HTTP.Data.Listen.Port.Override";
		public static final String BCFG_WEBSEED_ACTIVATION_USES_AVAILABILITY = "webseed.activation.uses.availability";
		public static final String BCFG_WEBSEED_ACTIVATION_MIN_SPEED_KBPS = "webseed.activation.min.speed.kbps";
		public static final String BCFG_PREFIX_PEER_SRC_SELECTION_DEF = "Peer Source Selection Default.";
		public static final String BCFG_PREFIX_NETWORK_SELECTION_DEF = "Network Selection Default.";
		public static final String BCFG_NETWORK_SELECTION_PROMPT = "Network Selection Prompt";
		public static final String ICFG_NETWORK_MAX_SIMULTANEOUS_CONNECT_ATTEMPTS = "network.max.simultaneous.connect.attempts";
		public static final String ICFG_NETWORK_TCP_MAX_CONNECTIONS_OUTSTANDING = "network.tcp.max.connections.outstanding";
		public static final String SCFG_BIND_IP = "Bind IP";
		public static final String BCFG_CHECK_BIND_IP_ON_START = "Check Bind IP On Start";
		public static final String BCFG_ENFORCE_BIND_IP = "Enforce Bind IP";
		public static final String BCFG_ENFORCE_BIND_IP_PAUSE = "Enforce Bind IP Pause";
		public static final String BCFG_SHOW_IP_BINDINGS_ICON = "Show IP Bindings Icon";
		public static final String BCFG_NETWORK_ADMIN_MAYBE_VPN_ENABLE = "network.admin.maybe.vpn.enable";
		public static final String ICFG_NETWORK_BIND_LOCAL_PORT = "network.bind.local.port";
		public static final String ICFG_NETWORK_TCP_MTU_SIZE = "network.tcp.mtu.size";
		public static final String ICFG_NETWORK_TCP_SOCKET_SO_SNDBUF = "network.tcp.socket.SO_SNDBUF";
		public static final String ICFG_NETWORK_TCP_SOCKET_SO_RCVBUF = "network.tcp.socket.SO_RCVBUF";
		public static final String SCFG_NETWORK_TCP_SOCKET_IPDIFF_SERV = "network.tcp.socket.IPDiffServ";
		public static final String ICFG_NETWORK_TCP_READ_SELECT_TIME = "network.tcp.read.select.time";
		public static final String ICFG_NETWORK_TCP_READ_SELECT_MIN_TIME = "network.tcp.read.select.min.time";
		public static final String ICFG_NETWORK_TCP_WRITE_SELECT_TIME = "network.tcp.write.select.time";
		public static final String ICFG_NETWORK_TCP_WRITE_SELECT_MIN_TIME = "network.tcp.write.select.min.time";
		public static final String BCFG_IPV_6_ENABLE_SUPPORT = "IPV6 Enable Support";
		public static final String BCFG_IPV_6_CHECK_MULTIPLE_ADDRESS_CHECKS = "IPV6 Enable Multiple Address Checks";
		public static final String SCFG_IPV_6_EXTRA_GLOBALS = "IPV6 Extra Globals";
		public static final String BCFG_IPV_6_PREFER_ADDRESSES = "IPV6 Prefer Addresses";
		public static final String BCFG_IPV_4_PREFER_STACK = "IPV4 Prefer Stack";
		public static final String BCFG_IPV_4_IGNORE_NI_ADDRESSES = "IPV4 Ignore Network Addresses";
		public static final String BCFG_IPV_6_IGNORE_NI_ADDRESSES = "IPV6 Ignore Network Addresses";
		public static final String SCFG_NETWORK_ADDITIONAL_SERVICE_BINDS = "Additional Service Bind IPs";
		public static final String BCFG_NETWORK_IGNORE_BIND_FOR_LAN = "Ignore Bind For LAN";
		
		public static final String SCFG_DNS_ALT_SERVERS = "DNS Alt Servers";
		public static final String BCFG_DNS_ALT_SERVERS_SOCKS_ENABLE = "DNS Alt Servers SOCKS Enable";
		public static final String BCFG_NETWORK_TRANSPORT_ENCRYPTED_REQUIRE = "network.transport.encrypted.require";
		public static final String SCFG_NETWORK_TRANSPORT_ENCRYPTED_MIN_LEVEL = "network.transport.encrypted.min_level";
		public static final String BCFG_NETWORK_TRANSPORT_ENCRYPTED_FALLBACK_OUTGOING = "network.transport.encrypted.fallback.outgoing";
		public static final String BCFG_NETWORK_TRANSPORT_ENCRYPTED_FALLBACK_INCOMING = "network.transport.encrypted.fallback.incoming";
		public static final String BCFG_NETWORK_TRANSPORT_ENCRYPTED_USE_CRYPTO_PORT = "network.transport.encrypted.use.crypto.port";
		public static final String BCFG_ENABLE_PROXY = "Enable.Proxy";
		public static final String BCFG_ENABLE_SOCKS = "Enable.SOCKS";
		public static final String SCFG_PROXY_HOST = "Proxy.Host";
		public static final String SCFG_PROXY_PORT = "Proxy.Port";
		public static final String SCFG_PROXY_USERNAME = "Proxy.Username";
		public static final String SCFG_PROXY_PASSWORD = "Proxy.Password";
		public static final String BCFG_PROXY_SOCKS_TRACKER_DNS_DISABLE = "Proxy.SOCKS.Tracker.DNS.Disable";
		public static final String BCFG_PROXY_DATA_ENABLE = "Proxy.Data.Enable";
		public static final String BCFG_PROXY_DATA_SOCKS_INFORM = "Proxy.Data.SOCKS.inform";
		public static final String SCFG_PROXY_DATA_SOCKS_VERSION = "Proxy.Data.SOCKS.version";
		public static final String BCFG_PROXY_DATA_SAME = "Proxy.Data.Same";
		public static final String SCFG_PREFIX_PROXY_DATA_HOST = "Proxy.Data.Host";
		public static final String SCFG_PREFIX_PROXY_DATA_PORT = "Proxy.Data.Port";
		public static final String SCFG_PREFIX_PROXY_DATA_USERNAME = "Proxy.Data.Username";
		public static final String SCFG_PREFIX_PROXY_DATA_PASSWORD = "Proxy.Data.Password";
		public static final String BCFG_PROXY_SOCKS_DISABLE_PLUGIN_PROXIES = "Proxy.SOCKS.disable.plugin.proxies";
		public static final String BCFG_PROXY_CHECK_ON_START = "Proxy.Check.On.Start";
		public static final String BCFG_PROXY_SOCKS_SHOW_ICON = "Proxy.SOCKS.ShowIcon";
		public static final String BCFG_PROXY_SOCKS_SHOW_ICON_FLAG_INCOMING = "Proxy.SOCKS.ShowIcon.FlagIncoming";
		public static final String SCFG_CONNECTION_TEST_DOMAIN = "Connection.Test.Domain";
	}

	public static class File {
		public static final String BCFG_DEFAULT_DIR_BEST_GUESS = "DefaultDir.BestGuess";
		public static final String SCFG_DEFAULT_SAVE_PATH = "Default save path";
		public static final String BCFG_ALWAYS_CREATE_TORRENT_SUB_FOLDER = "Always Create Torrent Sub-Folder";
		public static final String BCFG_UI_ADDTORRENT_OPENOPTIONS_SEP = "ui.addtorrent.openoptions.sep";
		public static final String BCFG_DEFAULT_DIR_AUTO_SAVE_AUTO_RENAME = "DefaultDir.AutoSave.AutoRename";
		public static final String ICFG_UI_ADDTORRENT_OPENOPTIONS_AUTO_CLOSE_SECS = "ui.addtorrent.openoptions.auto.close.secs";
		public static final String BCFG_DEFAULT_DIR_AUTO_UPDATE = "DefaultDir.AutoUpdate";
		public static final String ICFG_SAVE_TO_LIST_MAX_ENTRIES = "saveTo_list.max_entries";
		public static final String SCFG_SAVE_TO_LIST = "saveTo_list";
		public static final String BCFG_XFS_ALLOCATION = "XFS Allocation";
		public static final String BCFG_ZERO_NEW = "Zero New";
		public static final String BCFG_ZERO_NEW_STOP = "Zero New Stop";
		public static final String BCFG_ENABLE_REORDER_STORAGE_MODE = "Enable reorder storage mode";
		public static final String ICFG_REORDER_STORAGE_MODE_MIN_MB = "Reorder storage mode min MB";
		public static final String BCFG_ENABLE_INCREMENTAL_FILE_CREATION = "Enable incremental file creation";
		public static final String BCFG_FILE_TRUNCATE_IF_TOO_LARGE = "File.truncate.if.too.large";
		public static final String BCFG_MERGE_SAME_SIZE_FILES = "Merge Same Size Files";
		public static final String BCFG_MERGE_SAME_SIZE_FILES_EXTENDED = "Merge Same Size Files Extended";
		public static final String ICFG_MERGE_SAME_SIZE_FILES_TOLERANCE = "Merge Same Size Files Tolerance";
		public static final String ICFG_MERGE_SAME_SIZE_FILES_MIN_PIECES = "Merge Same Size Files Min Pieces";
		public static final String BCFG_CHECK_PIECES_ON_COMPLETION = "Check Pieces on Completion";
		public static final String BCFG_CHECK_PIECES_ON_COMPLETION_BEFORE_MOVE = "Check Pieces on Completion Before Move";
		public static final String BCFG_SEEDING_PIECE_CHECK_RECHECK_ENABLE = "Seeding Piece Check Recheck Enable";
		public static final String BCFG_FILE_STRICT_LOCKING = "File.strict.locking";
		public static final String ICFG_MAX_FILE_LINKS_SUPPORTED = "Max File Links Supported";
		public static final String BCFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART = "Insufficient Space Download Restart Enable";
		public static final String ICFG_INSUFFICIENT_SPACE_DOWNLOAD_RESTART_MINS = "Insufficient Space Download Restart Period";
		public static final String BCFG_MISSING_FILE_DOWNLOAD_RESTART = "Missing File Download Restart Enable";
		public static final String BCFG_SKIP_COMP_DL_FILE_CHECKS = "Skip Complete Download File Checks";
		public static final String BCFG_SKIP_INCOMP_DL_FILE_CHECKS = "Skip Incomplete Download File Checks";
		public static final String ICFG_MISSING_FILE_DOWNLOAD_RESTART_MINS = "Missing File Download Restart Period";
		public static final String ICFG_SAVE_RESUME_INTERVAL = "Save Resume Interval";
		public static final String BCFG_ON_RESUME_RECHECK_ALL = "On Resume Recheck All";
		public static final String BCFG_FILE_SAVE_PEERS_ENABLE = "File.save.peers.enable";
		public static final String ICFG_FILE_SAVE_PEERS_MAX = "File.save.peers.max";
		public static final String BCFG_DISABLE_SAVE_INTERIM_DOWNLOAD_STATE = "Disable Interim Download State Save";
		public static final String SCFG_PRIORITY_EXTENSIONS = "priorityExtensions";
		public static final String BCFG_PRIORITY_EXTENSIONS_IGNORE_CASE = "priorityExtensionsIgnoreCase";
		public static final String SCFG_QUICK_VIEW_EXTS = "quick.view.exts";
		public static final String ICFG_QUICK_VIEW_MAXKB = "quick.view.maxkb";
		public static final String BCFG_RENAME_INCOMPLETE_FILES = "Rename Incomplete Files";
		public static final String SCFG_RENAME_INCOMPLETE_FILES_EXTENSION = "Rename Incomplete Files Extension";
		public static final String BCFG_ENABLE_SUBFOLDER_FOR_DND_FILES = "Enable Subfolder for DND Files";
		public static final String SCFG_SUBFOLDER_FOR_DND_FILES = "Subfolder for DND Files";
		/** Despite name, this is the DND file prefix when SCFG_SUBFOLDER_FOR_DND_FILES is true */
		public static final String BCFG_USE_INCOMPLETE_FILE_PREFIX = "Use Incomplete File Prefix";
		public static final String BCFG_DOWNLOAD_HISTORY_ENABLED = "Download History Enabled";
		public static final String BCFG_FILES_AUTO_TAG_ENABLE = "Files Auto Tag Enable";
		public static final String ICFG_FILES_AUTO_TAG_COUNT = "Files Auto Tag Count";
		public static final String SCFG_FILE_AUTO_TAG_NAME_DEFAULT = "File Auto Tag Name Default";
		public static final String BCFG_FILES_AUTO_TAG_BEST_SIZE = "Files Auto Tag Best Size";
		public static final String BCFG_FILES_AUTO_TAG_ALLOW_MOD = "Files Auto Tag Mod Enable";
		public static final String SCFG_FILE_TORRENT_AUTO_SKIP_EXTENSIONS = "File.Torrent.AutoSkipExtensions";
		public static final String SCFG_FILE_TORRENT_AUTO_SKIP_FILES = "File.Torrent.AutoSkipFiles";
		public static final String BCFG_FILE_TORRENT_AUTO_SKIP_FILES_REG_EXP = "File.Torrent.AutoSkipFiles.RegExp";
		public static final String ICFG_FILE_TORRENT_AUTO_SKIP_MIN_SIZE_KB = "File.Torrent.AutoSkipMinSizeKB";
		public static final String SCFG_FILE_TORRENT_IGNORE_FILES = "File.Torrent.IgnoreFiles";
		public static final String SCFG_FILE_CHARACTER_CONVERSIONS = "File.Character.Conversions";
		public static final String ICFG_TB_CONFIRM_DELETE_CONTENT = "tb.confirm.delete.content";
		public static final String BCFG_DEF_DELETETORRENT = "def.deletetorrent";
		public static final String BCFG_DEF_DELETEALLSELECTED = "def.deleteallselected";
		public static final String BCFG_MOVE_DELETED_DATA_TO_RECYCLE_BIN = "Move Deleted Data To Recycle Bin";
		public static final String BCFG_FILE_DELETE_INCLUDE_FILES_OUTSIDE_SAVE_DIR = "File.delete.include_files_outside_save_dir";
		public static final String BCFG_DELETE_PARTIAL_FILES_ON_LIBRARY_REMOVAL = "Delete Partial Files On Library Removal";
		public static final String BCFG_USE_CONFIG_FILE_BACKUPS = "Use Config File Backups";
		public static final String BCFG_MOVE_COMPLETED_WHEN_DONE = "Move Completed When Done";
		public static final String SCFG_COMPLETED_FILES_DIRECTORY = "Completed Files Directory";
		public static final String BCFG_MOVE_TORRENT_WHEN_DONE = "Move Torrent When Done";
		public static final String SCFG_MOVE_TORRENT_WHEN_DONE_DIRECTORY = "Move Torrent When Done Directory";
		public static final String BCFG_MOVE_ONLY_WHEN_IN_DEFAULT_SAVE_DIR = "Move Only When In Default Save Dir";
		public static final String BCFG_FILE_MOVE_DOWNLOAD_REMOVED_ENABLED = "File.move.download.removed.enabled";
		public static final String SCFG_FILE_MOVE_DOWNLOAD_REMOVED_PATH = "File.move.download.removed.path";
		public static final String BCFG_FILE_MOVE_DOWNLOAD_REMOVED_MOVE_TORRENT = "File.move.download.removed.move_torrent";
		public static final String SCFG_FILE_MOVE_DOWNLOAD_REMOVED_MOVE_TORRENT_PATH = "File.move.download.removed.move_torrent_path";
		public static final String BCFG_FILE_MOVE_DOWNLOAD_REMOVED_ONLY_IN_DEFAULT = "File.move.download.removed.only_in_default";
		public static final String BCFG_FILE_MOVE_DOWNLOAD_REMOVED_MOVE_PARTIAL = "File.move.download.removed.move_partial";
		public static final String BCFG_FILE_MOVE_ADD_SUB_FOLDER = "File.move.add.sub.dir";
		public static final String BCFG_FILE_USE_TEMP_AND_MOVE_ENABLE = "file.use.temp.path.and.move.enable";
		public static final String BCFG_FILE_MOVE_ORIGIN_DELETE_FAIL_IS_WARNING = "Fail To Delete Origin File After Move Is Warning";
		public static final String SCFG_FILE_USE_TEMP_AND_MOVE_PATH = "file.use.temp.path.and.move.path";
		public static final String BCFG_COPY_AND_DELETE_DATA_RATHER_THAN_MOVE = "Copy And Delete Data Rather Than Move";
		public static final String ICFG_DISKMANAGER_PERF_CACHE_NOTSMALLERTHAN = "diskmanager.perf.cache.notsmallerthan";
		public static final String BCFG_DISKMANAGER_PERF_CACHE_ENABLE_READ = "diskmanager.perf.cache.enable.read";
		public static final String BCFG_DISKMANAGER_PERF_CACHE_ENABLE_WRITE = "diskmanager.perf.cache.enable.write";
		public static final String BCFG_DISKMANAGER_PERF_CACHE_FLUSHPIECES = "diskmanager.perf.cache.flushpieces";
		public static final String BCFG_DISKMANAGER_PERF_CACHE_TRACE = "diskmanager.perf.cache.trace";
		public static final String ICFG_DISKMANAGER_HASHCHECKING_STRATEGY = "diskmanager.hashchecking.strategy";
		public static final String BCFG_DISKMANAGER_HASHCHECKING_SMALLESTFIRST = "diskmanager.hashchecking.smallestfirst";
		public static final String BCFG_DISKMANAGER_ALLOC_SMALLESTFIRST = "diskmanager.alloc.smallestfirst";
		public static final String BCFG_DISKMANAGER_MOVE_SMALLESTFIRST = "diskmanager.move.smallestfirst";
		public static final String BCFG_DISKMANAGER_ONE_OP_PER_FS = "diskmanager.one.op.per.fs";
		public static final String BCFG_DISKMANAGER_ONE_OP_PER_FS_CONC_READ = "diskmanager.one.op.per.fs.conc.read";
		public static final String BCFG_DISKMANAGER_HASHCHECKING_MAX_ACTIVE = "diskmanager.hashchecking.maxactive";
		public static final String BCFG_DISKMANAGER_PERF_CACHE_ENABLE = "diskmanager.perf.cache.enable";
		public static final String ICFG_DISKMANAGER_PERF_CACHE_SIZE = "diskmanager.perf.cache.size";
		public static final String ICFG_FILE_MAX_OPEN = "File Max Open";
		public static final String ICFG_DISKMANAGER_PERF_WRITE_MAXMB = "diskmanager.perf.write.maxmb";
		public static final String ICFG_DISKMANAGER_PERF_READ_MAXMB = "diskmanager.perf.read.maxmb";
		public static final String BCFG_MOVE_IF_ON_SAME_DRIVE = "Move If On Same Drive";
		public static final String BCFG_FILE_MOVE_SUBDIR_IS_DEFAULT = "File.move.subdir_is_default";
		public static final String ICFG_WATCH_TORRENT_FOLDER_PATH_COUNT = "Watch Torrent Folder Path Count";
		public static final String ICFG_WATCH_TORRENT_FOLDER_INTERVAL_SECS = "Watch Torrent Folder Interval Secs";
		//public static final String BCFG_START_WATCHED_TORRENTS_STOPPED = "Start Watched Torrents Stopped";
		public static final String ICFG_WATCH_TORRENT_FOLDER_ADD_MODE = "Watch Torrents Add Mode";
		public static final String BCFG_WATCH_TORRENT_ALWAYS_RENAME = "Watch Torrent Always Rename";
		public static final String BCFG_WATCH_TORRENT_USE_TOD = "Watch Torrent Use TOD";
		public static final String BCFG_TORRENT_MONITOR_CLIPBOARD = "Monitor Clipboard For Torrents";
		public static final String BCFG_SAVE_TORRENT_FILES = "Save Torrent Files";
		public static final String SCFG_GENERAL_DEFAULT_TORRENT_DIRECTORY = "General_sDefaultTorrent_Directory";
		public static final String BCFG_SAVE_TORRENT_BACKUP = "Save Torrent Backup";
		public static final String BCFG_DELETE_SAVED_TORRENT_FILES = "Delete Saved Torrent Files";
		public static final String BCFG_DELETE_ORIGINAL_TORRENT_FILES = "Delete Original Torrent Files";
		public static final String BCFG_DEFAULT_START_TORRENTS_STOPPED = "Default Start Torrents Stopped";
		public static final String BCFG_DEFAULT_START_TORRENTS_STOPPED_AUTO_PAUSE = "Default Start Torrents Stopped Auto Pause";
		public static final String BCFG_WATCH_TORRENT_FOLDER = "Watch Torrent Folder";
		public static final String SCFG_PREFIX_WATCH_TORRENT_FOLDER_PATH = "Watch Torrent Folder Path";
		public static final String SCFG_PREFIX_WATCH_TORRENT_FOLDER_TAG = "Watch Torrent Folder Tag";
		public static final String SCFG_FILE_DECODER_DEFAULT = "File.Decoder.Default";
		public static final String BCFG_FILE_DECODER_PROMPT = "File.Decoder.Prompt";
		public static final String BCFG_FILE_DECODER_SHOW_LAX = "File.Decoder.ShowLax";
		public static final String BCFG_FILE_DECODER_SHOW_ALL = "File.Decoder.ShowAll";
		public static final String BCFG_ENABLE_SPARSE_FILES = "Enable Sparse Files";
		public static final String SCFG_FILE_AUTO_SEQUENTIAL_EXTS = "file.auto.sequential.exts";
		public static final String SCFG_PREFIX_FILE_AUTO_TAG_EXTS = "File Auto Tag Exts ";
		public static final String SCFG_PREFIX_FILE_AUTO_TAG_NAME = "File Auto Tag Name ";
	}

	public static class IPFilter {
		public static final String BCFG_IP_FILTER_ENABLED = "Ip Filter Enabled";
		public static final String BCFG_IP_FILTER_ALLOW = "Ip Filter Allow";
		public static final String BCFG_IP_FILTER_BANNING_PERSISTENT = "Ip Filter Banning Persistent";
		public static final String BCFG_IP_FILTER_DISABLE_FOR_UPDATES = "Ip Filter Disable For Updates";
		public static final String BCFG_IP_FILTER_ENABLE_BANNING = "Ip Filter Enable Banning";
		public static final String FCFG_IP_FILTER_BAN_DISCARD_RATIO = "Ip Filter Ban Discard Ratio";
		public static final String ICFG_IP_FILTER_BAN_DISCARD_MIN_KB = "Ip Filter Ban Discard Min KB";
		public static final String ICFG_IP_FILTER_BAN_BLOCK_LIMIT = "Ip Filter Ban Block Limit";
		public static final String BCFG_IP_FILTER_DONT_BAN_LAN = "Ip Filter Dont Ban LAN";
		public static final String ICFG_IP_FILTER_AUTOLOAD_DAYS = "Ip Filter Autoload Days";
		public static final String ICFG_IP_FILTER_AUTOLOAD_LAST = "Ip Filter Autoload Last Date";
		public static final String SCFG_IP_FILTER_AUTOLOAD_FILE = "Ip Filter Autoload File";
		public static final String SCFG_IP_FILTER_V6_AUTOLOAD_FILE = "Ip Filter V6 Autoload File";
		public static final String BCFG_IP_FILTER_CLEAR_ON_RELOAD = "Ip Filter Clear On Reload";
		public static final String BCFG_IP_FILTER_ENABLE_DESCRIPTION_CACHE = "Ip Filter Enable Description Cache";
	}

	public static class Logging {
		public static final String BCFG_LOGGER_ENABLED = "Logger.Enabled";
		public static final String BCFG_LOGGING_ENABLE = "Logging Enable";
		public static final String SCFG_LOGGING_DIR = "Logging Dir";
		public static final String ICFG_LOGGING_MAX_SIZE = "Logging Max Size";
		public static final String SCFG_LOGGING_TIMESTAMP = "Logging Timestamp";
		public static final String BCFG_LOGGER_DEBUG_FILES_DISABLE = "Logger.DebugFiles.Disable";
		public static final String BCFG_LOGGER_DEBUG_FILES_FORCE = "Logger.DebugFiles.Enabled.Force";
		public static final String ICFG_LOGGER_DEBUG_FILES_SIZE_KB = "Logger.DebugFiles.SizeKB";
	}

	public static class Security {
		public static final String BCFG_SECURITY_CERT_AUTO_INSTALL = "security.cert.auto.install";
		public static final String BCFG_SECURITY_CERT_AUTO_DECLINE = "security.cert.auto.decline";
		public static final String SCFG_SECURITY_JAR_TOOLS_DIR = "Security.JAR.tools.dir";
	}

	public static class Sharing {
		public static final String SCFG_SHARING_PROTOCOL = "Sharing Protocol";
		public static final String BCFG_SHARING_TORRENT_PRIVATE = "Sharing Torrent Private";
		public static final String BCFG_SHARING_PERMIT_DHT = "Sharing Permit DHT";
		public static final String BCFG_SHARING_ADD_HASHES = "Sharing Add Hashes";
		public static final String BCFG_SHARING_DISABLE_RCM = "Sharing Disable RCM";
		public static final String BCFG_SHARING_RESCAN_ENABLE = "Sharing Rescan Enable";
		public static final String ICFG_SHARING_RESCAN_PERIOD = "Sharing Rescan Period";
		public static final String SCFG_SHARING_TORRENT_COMMENT = "Sharing Torrent Comment";
		public static final String BCFG_SHARING_IS_PERSISTENT = "Sharing Is Persistent";
		public static final String BCFG_SHARING_NETWORK_SELECTION_GLOBAL = "Sharing Network Selection Global";
		public static final String BCFG_PREFIX_SHARING_NETWORK_SELECTION_DEFAULT = "Sharing Network Selection Default.";
	}

	public static class StartupShutdown {
		public static final String BCFG_START_ON_LOGIN = "Start On Login";
		public static final String BCFG_START_IN_LOW_RESOURCE_MODE = "Start In Low Resource Mode";
		public static final String BCFG_LRMS_UI = "LRMS UI";
		public static final String BCFG_LRMS_UDP_PEERS = "LRMS UDP Peers";
		public static final String BCFG_LRMS_DHT_SLEEP = "LRMS DHT Sleep";
		public static final String BCFG_PREVENT_SLEEP_DOWNLOADING = "Prevent Sleep Downloading";
		public static final String SCFG_PREVENT_SLEEP_TAG = "Prevent Sleep Tag";
		public static final String BCFG_PAUSE_DOWNLOADS_ON_EXIT = "Pause Downloads On Exit";
		public static final String BCFG_RESUME_DOWNLOADS_ON_START = "Resume Downloads On Start";
		public static final String BCFG_STOP_TRIGGERS_AUTO_RESET = "Stop Triggers Auto Reset";
		public static final String BCFG_PROMPT_TO_ABORT_SHUTDOWN = "Prompt To Abort Shutdown";
		public static final String ICFG_STOP_FORCE_TERMINATE_AFTER = "Force Terminate After Mins";
		public static final String ICFG_AUTO_RESTART_WHEN_IDLE = "Auto Restart When Idle";
		public static final String BCFG_AUTO_RESTART_WHEN_IDLE_PROMPT = "Auto Restart When Idle Prompt";
		public static final String SCFG_ON_DOWNLOADING_COMPLETE_DO = "On Downloading Complete Do";
		public static final String SCFG_ON_DOWNLOADING_COMPLETE_SCRIPT = "On Downloading Complete Script";
		public static final String SCFG_ON_SEEDING_COMPLETE_DO = "On Seeding Complete Do";
		public static final String SCFG_ON_SEEDING_COMPLETE_SCRIPT = "On Seeding Complete Script";
		public static final String BCFG_PREVENT_SLEEP_FP_SEEDING = "Prevent Sleep FP Seeding";
	}

	public static class Stats {
		public static final String ICFG_STATS_SMOOTHING_SECS = "Stats Smoothing Secs";
		public static final String BCFG_STATS_GRAPH_DIVIDERS = "Stats Graph Dividers";
		public static final String BCFG_STATS_ENABLE = "Stats Enable";
		public static final String SCFG_STATS_DIR = "Stats Dir";
		public static final String SCFG_STATS_XSL_FILE = "Stats XSL File";
		public static final String ICFG_STATS_PERIOD = "Stats Period";
		public static final String BCFG_STATS_EXPORT_PEER_DETAILS = "Stats Export Peer Details";
		public static final String BCFG_STATS_EXPORT_FILE_DETAILS = "Stats Export File Details";
		public static final String BCFG_LONG_TERM_STATS_ENABLE = "long.term.stats.enable";
		public static final String ICFG_LONG_TERM_STATS_WEEKSTART = "long.term.stats.weekstart";
		public static final String ICFG_LONG_TERM_STATS_MONTHSTART = "long.term.stats.monthstart";
	}


	public static class Tracker {
		public static final String SCFG_TRACKER_IP = "Tracker IP";
		public static final String BCFG_TRACKER_CLIENT_SCRAPE_ENABLE = "Tracker Client Scrape Enable";
		public static final String BCFG_TRACKER_CLIENT_SCRAPE_STOPPED_ENABLE = "Tracker Client Scrape Stopped Enable";
		public static final String BCFG_TRACKER_CLIENT_SCRAPE_NEVER_STARTED_DISABLE = "Tracker Client Scrape Never Started Disable";
		public static final String BCFG_TRACKER_CLIENT_SCRAPE_SINGLE_ONLY = "Tracker Client Scrape Single Only";
		public static final String BCFG_TRACKER_CLIENT_SEND_OS_AND_JAVA_VERSION = "Tracker Client Send OS and Java Version";
		public static final String BCFG_TRACKER_CLIENT_SHOW_WARNINGS = "Tracker Client Show Warnings";
		public static final String BCFG_TRACKER_CLIENT_EXCLUDE_LAN = "Tracker Client Exclude LAN";
		public static final String BCFG_TRACKER_CLIENT_ENABLE_TCP = "Tracker Client Enable TCP";
		public static final String BCFG_SERVER_ENABLE_UDP = "Server Enable UDP";
		public static final String BCFG_TRACKER_UDP_PROBE_ENABLE = "Tracker UDP Probe Enable";
		public static final String BCFG_TRACKER_DNS_RECORDS_ENABLE = "Tracker DNS Records Enable";
		public static final String SCFG_OVERRIDE_IP = "Override Ip";
		public static final String SCFG_TCP_LISTEN_PORT_OVERRIDE = "TCP.Listen.Port.Override";
		public static final String BCFG_TRACKER_CLIENT_NO_PORT_ANNOUNCE = "Tracker Client No Port Announce";
		public static final String BCFG_TRACKER_CLIENT_SMART_ACTIVATION = "Tracker Client Smart Activation";
		public static final String ICFG_TRACKER_CLIENT_NUMWANT_LIMIT = "Tracker Client Numwant Limit";
		public static final String ICFG_TRACKER_CLIENT_MIN_ANNOUNCE_INTERVAL = "Tracker Client Min Announce Interval";
		public static final String ICFG_TRACKER_CLIENT_CONNECT_TIMEOUT = "Tracker Client Connect Timeout";
		public static final String ICFG_TRACKER_CLIENT_READ_TIMEOUT = "Tracker Client Read Timeout";
		public static final String ICFG_TRACKER_CLIENT_CLOSEDOWN_TIMEOUT = "Tracker Client Closedown Timeout";
		public static final String ICFG_TRACKER_CLIENT_CONCURRENT_ANNOUNCE = "Tracker Client Concurrent Announce";
		public static final String BCFG_TRACKER_KEY_ENABLE_CLIENT = "Tracker Key Enable Client";
		public static final String BCFG_TRACKER_SEPARATE_PEER_I_DS = "Tracker Separate Peer IDs";
		public static final String BCFG_TRACKER_PORT_ENABLE = "Tracker Port Enable";
		public static final String ICFG_TRACKER_PORT = "Tracker Port";
		public static final String SCFG_TRACKER_PORT_BACKUPS = "Tracker Port Backups";
		public static final String BCFG_TRACKER_PORT_SSL_ENABLE = "Tracker Port SSL Enable";
		public static final String ICFG_TRACKER_PORT_SSL = "Tracker Port SSL";
		public static final String SCFG_TRACKER_PORT_SSL_BACKUPS = "Tracker Port SSL Backups";
		public static final String BCFG_TRACKER_I2P_ENABLE = "Tracker I2P Enable";
		public static final String SCFG_TRACKER_I2P_HOST_PORT = "Tracker I2P Host Port";
		public static final String BCFG_TRACKER_TOR_ENABLE = "Tracker Tor Enable";
		public static final String SCFG_TRACKER_TOR_HOST_PORT = "Tracker Tor Host Port";
		public static final String BCFG_TRACKER_PUBLIC_ENABLE = "Tracker Public Enable";
		public static final String BCFG_TRACKER_PUBLIC_ENABLE_KNOWN_ONLY = "Tracker Public Enable Known Only";
		public static final String BCFG_TRACKER_PORT_FORCE_EXTERNAL = "Tracker Port Force External";
		public static final String BCFG_TRACKER_HOST_ADD_OUR_ANNOUNCE_URLS = "Tracker Host Add Our Announce URLs";
		public static final String BCFG_TRACKER_PASSWORD_ENABLE_WEB = "Tracker Password Enable Web";
		public static final String BCFG_TRACKER_PASSWORD_WEB_HTTPS_ONLY = "Tracker Password Web HTTPS Only";
		public static final String BCFG_TRACKER_PASSWORD_ENABLE_TORRENT = "Tracker Password Enable Torrent";
		public static final String SCFG_TRACKER_USERNAME = "Tracker Username";
		public static final String SCFG_TRACKER_PASSWORD = "Tracker Password";
		public static final String ICFG_TRACKER_POLL_INTERVAL_MIN = "Tracker Poll Interval Min";
		public static final String ICFG_TRACKER_POLL_INTERVAL_MAX = "Tracker Poll Interval Max";
		public static final String ICFG_TRACKER_POLL_INC_BY = "Tracker Poll Inc By";
		public static final String ICFG_TRACKER_POLL_INC_PER = "Tracker Poll Inc Per";
		public static final String ICFG_TRACKER_SCRAPE_RETRY_PERCENTAGE = "Tracker Scrape Retry Percentage";
		public static final String ICFG_TRACKER_SCRAPE_CACHE = "Tracker Scrape Cache";
		public static final String ICFG_TRACKER_ANNOUNCE_CACHE_MIN_PEERS = "Tracker Announce Cache Min Peers";
		public static final String ICFG_TRACKER_ANNOUNCE_CACHE = "Tracker Announce Cache";
		public static final String ICFG_TRACKER_MAX_PEERS_RETURNED = "Tracker Max Peers Returned";
		public static final String ICFG_TRACKER_MAX_SEEDS_RETAINED = "Tracker Max Seeds Retained";
		public static final String BCFG_TRACKER_NAT_CHECK_ENABLE = "Tracker NAT Check Enable";
		public static final String ICFG_TRACKER_NAT_CHECK_TIMEOUT = "Tracker NAT Check Timeout";
		public static final String BCFG_TRACKER_SEND_PEER_I_DS = "Tracker Send Peer IDs";
		public static final String BCFG_TRACKER_PORT_UDP_ENABLE = "Tracker Port UDP Enable";
		public static final String ICFG_TRACKER_PORT_UDP_VERSION = "Tracker Port UDP Version";
		public static final String BCFG_TRACKER_COMPACT_ENABLE = "Tracker Compact Enable";
		public static final String BCFG_TRACKER_LOG_ENABLE = "Tracker Log Enable";
		public static final String BCFG_TRACKER_KEY_ENABLE_SERVER = "Tracker Key Enable Server";
		public static final String SCFG_TRACKER_BANNED_CLIENTS = "Tracker Banned Clients";
		public static final String BCFG_PREFIX_TRACKER_NETWORK_SELECTION_DEFAULT = "Tracker Network Selection Default.";
		public static final String ICFG_TRACKER_MAX_GET_TIME = "Tracker Max GET Time";
		public static final String ICFG_TRACKER_MAX_POST_TIME_MULTIPLIER = "Tracker Max POST Time Multiplier";
		public static final String ICFG_TRACKER_MAX_THREADS = "Tracker Max Threads";
		public static final String BCFG_TRACKER_TCP_NON_BLOCKING = "Tracker TCP NonBlocking";
		public static final String ICFG_TRACKER_TCP_NON_BLOCKING_CONC_MAX = "Tracker TCP NonBlocking Conc Max";
	}

	public static class Transfer {

		public static final String ICFG_MAX_DOWNLOAD_SPEED_KBS = "Max Download Speed KBs";
		public static final String ICFG_MAX_UPLOAD_SPEED_KBS = "Max Upload Speed KBs";
		public static final String BCFG_ENABLE_SEEDINGONLY_UPLOAD_RATE = "enable.seedingonly.upload.rate";
		public static final String ICFG_MAX_UPLOAD_SPEED_SEEDING_KBS = "Max Upload Speed Seeding KBs";
		public static final String ICFG_MAX_UPLOADS_WHEN_BUSY_INC_MIN_SECS = "max.uploads.when.busy.inc.min.secs";
		public static final String BCFG_BIAS_UPLOAD_ENABLE = "Bias Upload Enable";
		public static final String ICFG_BIAS_UPLOAD_SLACK_KBS = "Bias Upload Slack KBs";
		public static final String BCFG_BIAS_UPLOAD_HANDLE_NO_LIMIT = "Bias Upload Handle No Limit";
		public static final String BCFG_AUTO_ADJUST_TRANSFER_DEFAULTS = "Auto Adjust Transfer Defaults";
		public static final String ICFG_MAX_UPLOADS = "Max Uploads";
		public static final String BCFG_ENABLE_SEEDINGONLY_MAXUPLOADS = "enable.seedingonly.maxuploads";
		public static final String ICFG_MAX_UPLOADS_SEEDING = "Max Uploads Seeding";
		public static final String ICFG_MAX_PEER_CONNECTIONS_PER_TORRENT = "Max.Peer.Connections.Per.Torrent";
		public static final String BCFG_MAX_PEER_CONNECTIONS_PER_TORRENT_WHEN_SEEDING_ENABLE = "Max.Peer.Connections.Per.Torrent.When.Seeding.Enable";
		public static final String ICFG_MAX_PEER_CONNECTIONS_PER_TORRENT_WHEN_SEEDING = "Max.Peer.Connections.Per.Torrent.When.Seeding";
		public static final String ICFG_MAX_PEER_CONNECTIONS_TOTAL = "Max.Peer.Connections.Total";
		public static final String ICFG_MAX_SEEDS_PER_TORRENT = "Max Seeds Per Torrent";
		public static final String ICFG_NON_PUBLIC_PEER_EXTRA_SLOTS_PER_TORRENT = "Non-Public Peer Extra Slots Per Torrent";
		public static final String ICFG_NON_PUBLIC_PEER_EXTRA_CONNECTIONS_PER_TORRENT = "Non-Public Peer Extra Connections Per Torrent";
		public static final String BCFG_USE_REQUEST_LIMITING = "Use Request Limiting";
		public static final String BCFG_USE_REQUEST_LIMITING_PRIORITIES = "Use Request Limiting Priorities";
		public static final String BCFG_UP_RATE_LIMITS_INCLUDE_PROTOCOL = "Up Rate Limits Include Protocol";
		public static final String BCFG_DOWN_RATE_LIMITS_INCLUDE_PROTOCOL = "Down Rate Limits Include Protocol";
		public static final String BCFG_ALLOW_SAME_IP_PEERS = "Allow Same IP Peers";
		public static final String ICFG_IPv4_IPv6_CONN_ACTION = "Dual IPV4 IPV6 Connection Action";
		public static final String BCFG_USE_LAZY_BITFIELD = "Use Lazy Bitfield";
		public static final String BCFG_PEERCONTROL_HIDE_PIECE = "peercontrol.hide.piece";
		public static final String BCFG_PEERCONTROL_HIDE_PIECE_DS = "peercontrol.hide.piece.ds";
		public static final String BCFG_PRIORITIZE_FIRST_PIECE = "Prioritize First Piece";
		public static final String ICFG_PRIORITIZE_FIRST_MB = "Prioritize First MB";
		public static final String BCFG_PRIORITIZE_FIRST_PIECE_FORCE = "Prioritize First Piece Force";
		public static final String BCFG_PRIORITIZE_MOST_COMPLETED_FILES = "Prioritize Most Completed Files";
		public static final String SCFG_IGNORE_PEER_PORTS = "Ignore.peer.ports";
		public static final String BCFG_LAN_SPEED_ENABLED = "LAN Speed Enabled";
		public static final String ICFG_MAX_LAN_UPLOAD_SPEED_K_BS = "Max LAN Upload Speed KBs";
		public static final String ICFG_MAX_LAN_DOWNLOAD_SPEED_K_BS = "Max LAN Download Speed KBs";
	}

	public static class AutoSpeed {

		public static final String ICFG_AUTO_SPEED_MIN_UPLOAD_KBS = "AutoSpeed Min Upload KBs";
		public static final String ICFG_AUTO_SPEED_MAX_UPLOAD_KBS = "AutoSpeed Max Upload KBs";
		public static final String BCFG_AUTO_SPEED_DOWNLOAD_ADJ_ENABLE = "AutoSpeed Download Adj Enable";
		public static final String FCFG_AUTO_SPEED_DOWNLOAD_ADJ_RATIO = "AutoSpeed Download Adj Ratio";
		public static final String ICFG_AUTO_SPEED_MAX_INCREMENT_KBS = "AutoSpeed Max Increment KBs";
		public static final String ICFG_AUTO_SPEED_MAX_DECREMENT_KBS = "AutoSpeed Max Decrement KBs";
		public static final String ICFG_AUTO_SPEED_CHOKING_PING_MILLIS = "AutoSpeed Choking Ping Millis";
		public static final String ICFG_AUTO_SPEED_FORCED_MIN_KBS = "AutoSpeed Forced Min KBs";
		public static final String ICFG_AUTO_SPEED_LATENCY_FACTOR = "AutoSpeed Latency Factor";
	}

	public static class UI {
		public static final String BCFG_LANG_UPPER_CASE = "label.lang.upper.case";
		public static final String SCFG_LOCALE = "locale";
	}
	
	public static class Tag {
		public static final String BCFG_TRACKER_AUTO_TAG_INTERESTING_TRACKERS = "Auto Tag Interesting Trackers";
		public static final String ICFG_TAG_AUTO_FULL_REAPPLY_PERIOD_SECS = "Tag Auto Full Reapply Period Secs";
		public static final String BCFG_TAG_SHOW_SWARM_TAGS_IN_OVERVIEW = "Show Swarm Tags In Overview";
	}
}
