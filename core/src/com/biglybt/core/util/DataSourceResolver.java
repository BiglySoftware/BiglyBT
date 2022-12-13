package com.biglybt.core.util;

import java.util.*;

import com.biglybt.core.devices.Device;
import com.biglybt.pifimpl.local.PluginCoreUtils;

public class 
DataSourceResolver
{
	public static final Object DEFAULT_DATASOURCE	= new Object();
	
	private static Map<String,Object>	importer_map = new HashMap<>();
	
	public static Map<String,Object>
	exportDataSource(
		Object		data_source )
	{
		if ( data_source instanceof List ){
			
			data_source = ((List)data_source).toArray( new Object[0]);
		}	
		
		if ( data_source instanceof ExportableDataSource ) {
			
			ExportedDataSource e = ((ExportableDataSource)data_source).exportDataSource();
			
			if ( e == null ) {
				
				return( null );
			}
			
			Map<String,Object>	result = new HashMap<>();
			
			result.put( "exporter", e.getExporter().getCanonicalName());
			result.put( "export", e.getExport());
			
			return( result );
		
		}else if ( data_source instanceof Object[] ){
			
			Object[] sources = (Object[])data_source;
			
			List<Map<String,Object>>	list = new ArrayList<>();
			
			Map<String,Object>	result = new HashMap<>();

			result.put( "exports", list );
			
			for ( Object ds: sources ) {
				
				list.add( exportDataSource( ds ));
			}
			
			return( result );
			
		}else if ( data_source instanceof Device ){
			
			// not required as resolved internally
			
			return( null );
			
		}else if ( data_source == DEFAULT_DATASOURCE ){
			
			return( null );
			
		}else{
			
			Object core_ds = PluginCoreUtils.convert( data_source, true );
			
			if ( core_ds != null && core_ds != data_source ){
				
				return( exportDataSource( core_ds ));
			}
			
			Object 	literal 		= null;
			int		literal_type	= 0;
			
			if ( data_source instanceof String ){
					
				literal 		= ((String)data_source).getBytes( Constants.UTF_8 );
				literal_type	= 1;
			}
			
			if ( literal != null ){
				
				Map<String,Object>	result = new HashMap<>();
				
				result.put( "literal_type", new Long( literal_type ));
				result.put( "literal", literal );
				
				return( result );
			}
			
			Debug.out( "Can't export a " + data_source );
		}
		
		return( null );
	}
	
	public static Object
	importDataSource(
		Map<String,Object>		map )
	{
		Object literal = map.get( "literal" );
		
		if ( literal != null ){
			
			try{
				int literal_type = ((Number)map.get( "literal_type" )).intValue();
			
				if ( literal_type == 1 ){
			
					if ( literal instanceof String ){
						
						return( literal );
					}
					
					return( new String((byte[])literal, Constants.UTF_8 ));
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		Runnable	callback = (Runnable)map.get( "callback" );
		
		List<Map<String,Object>> list = (List<Map<String,Object>>)map.get( "exports" );
		
		if ( list == null ) {
				 
			String exporter_class = (String)map.get( "exporter" );
			
			DataSourceImporter importer;
			
			synchronized( importer_map ) {
				
				Object temp = importer_map.get( exporter_class );
			
				if ( temp == null || temp instanceof List) {
				
					if ( callback == null ){
					
						Debug.out( "No importer for '" + exporter_class + "'" );
						
					}else {
						
						if ( temp == null ) {
							
							temp = new ArrayList();
							
							importer_map.put( exporter_class, temp );
						}
						
						((List)temp).add( callback );
					}
					
					return( null );
					
				}else{
					
					importer = (DataSourceImporter)temp;
				}
			}
			
			Map<String,Object> i_map = new HashMap<String,Object>((Map<String,Object>)map.get( "export" ));
			
			if ( callback != null ) {
				
				i_map.put( "callback", callback );
			}
			
			return( importer.importDataSource( i_map ));
			
		}else{
			
			Object[] data_sources = new Object[ list.size()];
			
			for (int i=0; i<data_sources.length; i++ ){
				
				Map<String,Object> m = list.get( i );
				
				data_sources[i] = importDataSource( m );
			}
			
			return( data_sources );
		}
	}
	
	
	public static void
	registerExporter(
		DataSourceImporter		exporter )
	{
		List<Runnable>	callbacks = null;
		
		synchronized( importer_map ) {
			
			String name = exporter.getClass().getCanonicalName();
			
			Object temp = importer_map.get( name );
			
			if ( temp instanceof List ) {
				
				callbacks = (List<Runnable>)temp;
			}
			
			importer_map.put( name, exporter );
		}
		
		if ( callbacks != null ) {
			
			for ( Runnable r: callbacks ) {
				
				try{
					r.run();
					
				}catch( Throwable e ) {
					
					Debug.out( e );
				}
			}
		}
	}
	
	public interface
	ExportableDataSource
	{
		public ExportedDataSource
		exportDataSource();
	}
	
	public interface
	ExportedDataSource
	{
		public Class<? extends DataSourceImporter>
		getExporter();
		
		public Map<String,Object>
		getExport();
	}
	
	public interface
	DataSourceImporter
	{
		public Object
		importDataSource(
			Map<String,Object>		map );
	}
}
