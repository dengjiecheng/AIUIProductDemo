package com.iflytek.aiuiproduct.handler.disposer;

import org.json.JSONObject;

import com.iflytek.aiuiproduct.handler.entity.SemanticResult;
import com.iflytek.aiuiproduct.handler.entity.ServiceType;
import com.iflytek.aiuiproduct.player.InsType;

import android.content.Context;

public class RadioDisposer extends Disposer {

	public RadioDisposer(Context context) {
		super(context);
	}

	@Override
	public void disposeResult(SemanticResult result) {
		
		JSONObject resultJson = result.getJson();
		String operation = resultJson.optString(KEY_OPERATION);
		String uuid = result.getUUID();
		
		if(OPERATION_INS.equals(operation)){
			parseRadioCommand(resultJson, uuid);
		}else{
			getPlayController().playText(uuid, result.getAnswerText());
		}
	}
	
	public boolean canHandle(ServiceType type){
		if(type == ServiceType.RADIO){
			return true;
		}else{
			return false;
		}
	}
	

	private void parseRadioCommand(JSONObject resultJson, String uuid) {
		try {
			JSONObject semantic = resultJson.getJSONObject(KEY_SEMANTIC);
			String insType = semantic.getJSONObject(KEY_SLOTS).getString(KEY_INSTYPE);
			
			//到音乐控制指令的转换
			if(insType.equalsIgnoreCase("CLOSE") | insType.equalsIgnoreCase("PAUSE") ){
				insType = "pause";
			}
			
			if(insType.equalsIgnoreCase("PLAY")){
				insType = "replay";
			}
			
			getPlayController().onMusicCommand(uuid, InsType.parseInsType(insType));
			return;
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
		
	
	

}
