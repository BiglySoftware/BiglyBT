package com.biglybt.core.util;

import java.util.*;

import com.biglybt.core.devices.Device;

public class 
DataSourceResolver
{
	private static Map<String,Object>	importer_map = new HashMap<>();
	
	public static Map<String,Object>
	exportDataSource(
		Object		data_source )
	{
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
			
		}else{
			
			Debug.out( "Can't export a " + data_source );
		}
		
		return( null );
	}
	
	public static Object
	importDataSource(
		Map<String,Object>		map )
	{
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
