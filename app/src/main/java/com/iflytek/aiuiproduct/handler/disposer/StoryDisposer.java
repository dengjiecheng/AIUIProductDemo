package com.iflytek.aiuiproduct.handler.disposer;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.iflytek.aiuiproduct.handler.entity.SemanticResult;
import com.iflytek.aiuiproduct.handler.entity.ServiceType;
import com.iflytek.aiuiproduct.player.entity.SongPlayInfo;

import android.content.Context;

public class StoryDisposer extends Disposer{
	private static final String KEY_URL = "playUrl";
	
	public StoryDisposer(Context context) {
		super(context);
	}
	
	@Override
	public void disposeResult(final SemanticResult result) {
		
		JSONObject resultJson = result.getJson();
		final List<SongPlayInfo> playList = new ArrayList<SongPlayInfo>();
		try {
			JSONObject dataJson = resultJson.getJSONObject(KEY_DATA);
			JSONArray resultJsonArray = dataJson.getJSONArray(KEY_RESULT);
			
			
			
			int size = resultJsonArray.length();
			for (int i = 0; i < size; i++) {
				JSONObject newsJson = resultJsonArray.optJSONObject(i);
				
				String url = newsJson.getString(KEY_URL);
				
				SongPlayInfo playInfo = new SongPlayInfo();
				
				if(i == 0){
				    playInfo.setAnswerText(result.getAnswerText());
				}else{
				    playInfo.setAnswerText("***");
				}
				
				playInfo.setPlayUrl(url);
				
				playList.add(playInfo);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		getPlayController().playSongList(result.getUUID(), playList,false);
		/*
		getPlayController().playText(result.getUUID(), result.getAnswerText(), 
				false, new Runnable() {
			
			@Override
			public void run() {
				getPlayController().playURLList(result.getUUID(), playList);
			}
		});*/
	}
	
	@Override
	public boolean canHandle(ServiceType type){
		if(type == ServiceType.STORY){
			return true;
		}else{
			return false;
		}
	}

}
