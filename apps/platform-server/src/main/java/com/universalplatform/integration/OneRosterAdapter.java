package com.universalplatform.integration;
import java.util.Map;
public interface OneRosterAdapter { Map<String,String> exportCsv(); ImportResult validateCsv(Map<String,String> files); record ImportResult(boolean valid,int fileCount,int rowCount,String message){} }
