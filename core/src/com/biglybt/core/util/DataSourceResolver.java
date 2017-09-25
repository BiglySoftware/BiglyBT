package com.biglybt.core.util;

import java.util.*;

public class 
DataSourceResolver
{
	private static Map<String,DataSourceImporter>	importer_map = new HashMap<>();
	
	public static Map<String,Object>
	exportDataSource(
		Object		data_source )
	{
		if ( data_source instanceof ExportableDataSource ) {
			
			ExportedDataSource e = ((ExportableDataSource)data_source).exportDataSource();
			
			Map<String,Object>	result = new HashMap<>();
			
			result.put( "exporter", e.getExporter().getCanonicalName());
			result.put( "export", e.getExport());
			
			return( result );
		}
		
		return( null );
	}
	
	public static Object
	importDataSource(
		Map<String,Object>		map )
	{
		String exporter_class = (String)map.get( "exporter" );
		
		DataSourceImporter importer;
		
		synchronized( importer_map ) {
			
			importer = importer_map.get( exporter_class );
		}
		
		if ( importer == null ) {
			
			return( null );
		}
		
		return( importer.importDataSource((Map<String,Object>)map.get( "export" )));
	}
	
	
	public static void
	registerExporter(
		DataSourceImporter		exporter )
	{
		synchronized( importer_map ) {
			
			importer_map.put( exporter.getClass().getCanonicalName(), exporter );
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
		
		public Map
		getExport();
	}
	
	public interface
	DataSourceImporter
	{
		public Object
		importDataSource(
			Map		map );
	}
}
