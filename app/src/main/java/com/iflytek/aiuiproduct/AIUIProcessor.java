package com.iflytek.aiuiproduct;

import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import com.iflytek.aiui.AIUIErrorCode;
import com.iflytek.aiui.servicekit.AIUIAgent;
import com.iflytek.aiui.servicekit.AIUIConstant;
import com.iflytek.aiui.servicekit.AIUIEvent;
import com.iflytek.aiui.servicekit.AIUIListener;
import com.iflytek.aiui.servicekit.AIUIMessage;
import com.iflytek.aiui.utils.log.DebugLog;
import com.iflytek.aiuiproduct.constant.ProductConstant;
import com.iflytek.aiuiproduct.handler.AsrResultHandler;
import com.iflytek.aiuiproduct.handler.SemanticResultHandler;
import com.iflytek.aiuiproduct.player.InsType;
import com.iflytek.aiuiproduct.player.PlayController;
import com.iflytek.aiuiproduct.player.PlayControllerListenerAdapter;
import com.iflytek.aiuiproduct.player.PlayController.PalyControllerItem;
import com.iflytek.aiuiproduct.utils.AppTimeLogger;
import com.iflytek.aiuiproduct.utils.AppTimeLogger.TimeLog;
import com.iflytek.aiuiproduct.utils.AppTimeLogger.TimeLogSaveListener;
import com.iflytek.aiuiproduct.utils.ConfigUtil;
import com.iflytek.aiuiproduct.utils.DevBoardControlUtil;
import com.iflytek.aiuiproduct.utils.FileUtil;
import com.iflytek.aiuiproduct.utils.NetworkUtil;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

/**
 * AIUI处理类
 * @author PR
 *
 */
public class AIUIProcessor extends PlayControllerListenerAdapter implements AIUIListener  {
		private static final String TAG = ProductConstant.TAG;
		
		private final static String WAV_PATH = "wav/";
		private final static String START_SUCCESS = WAV_PATH + "start_success.mp3";
		private final static String TONE_WRONG_APPID = WAV_PATH + "wrong_appid.mp3";
		
		private final static String XIAOAI_GREETING_EWZN = WAV_PATH + "xiaoai_greeting_ewzn.mp3";
		private final static String XIAOAI_GREETING_GS = WAV_PATH + "xiaoai_greeting_gs.mp3";
		private final static String XIAOAI_GREETING_SS = WAV_PATH + "xiaoai_greeting_ss.mp3";
		private final static String XIAOAI_GREETING_WTZN = WAV_PATH + "xiaoai_greeting_wtzn.mp3";
		private final static String XIAOAI_GOODBYE_NWZL = WAV_PATH + "xiaoai_goodbye_nwzl.mp3";
		
		private final static int Delay_SLEEP_TIME = 10000;
		// 唤醒后播放的欢迎音频
		private final static String[] WAKE_UP_TONES = {
			XIAOAI_GREETING_EWZN,		// 嗯，我在呢
			XIAOAI_GREETING_GS,			// 干啥
			XIAOAI_GREETING_SS,			// 啥事
			XIAOAI_GREETING_WTZN 		// 我听着呢
		};
		
		private final static String GRAMMAR_FILE_PATH = "grammar/grammar.bnf";
	

		private AIUIAgent mAIUIAgent;
	
		private Context mContext;
		// 音乐文本播放控制对象
		private PlayController mPlayController;
		// 判断是不是处于唤醒状态
		private boolean mIsWakeUp = false;
		private boolean mKeepWakeUp = false;
		
		private JSONObject mPreWakeUpJson;
		//导致休眠的错误码
		private int mSleepErrorCode = 0;
		//语义结果处理
		private SemanticResultHandler mSemanticHandler;
		//离线命令词处理
		private AsrResultHandler mAsrHandler;
		//延时休眠处理
		private Handler mDelaySleepHandler;
		// 休眠的广播接收者
		private BroadcastReceiver mSleepReceiver;
		//声音控制广播
		private BroadcastReceiver mVoiceCtrReceiver;
		
		public AIUIProcessor(Context context){
			mContext = context;

			// 关闭唤醒方向指示灯
			DevBoardControlUtil.sleepLight();

			// 设置WIFI指示灯
			DevBoardControlUtil.wifiStateLight(NetworkUtil.isNetworkAvailable(mContext));

			// 设置耗时日志监听器
			AppTimeLogger.setTimeLogSaveListener(mLogSaveListener);

			mPlayController = PlayController.getInstance(mContext);
			mPlayController.setPalyControllerListener(this);
			mPlayController.setMaxVolum();

			mSemanticHandler = new SemanticResultHandler(mContext);
			mAsrHandler = new AsrResultHandler(mContext);
			
			mDelaySleepHandler = new Handler();
			
			registerReceiver();
		}
		
		
		public void setAgent(AIUIAgent agent){
			mAIUIAgent = agent;
		}
		
		private void registerReceiver(){
			mSleepReceiver = new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
				    if(intent.getAction().equals(ProductConstant.ACTION_SLEEP)){
				        sayGoodBye("", false);
				    }else if(intent.getAction().equals(ProductConstant.ACTION_KEEP_WAKEUP)){
				        mKeepWakeUp = true;
				    }
					
				}

			};
			IntentFilter sleepIntentFilter = new IntentFilter();
			sleepIntentFilter.addAction(ProductConstant.ACTION_SLEEP);
			sleepIntentFilter.addAction(ProductConstant.ACTION_KEEP_WAKEUP);
			mContext.registerReceiver(mSleepReceiver,  sleepIntentFilter);
		
			mVoiceCtrReceiver = new BroadcastReceiver(){
				@Override
				public void onReceive(Context context, Intent intent) {
					String playMode = intent.getStringExtra("play_mode");
					if(playMode.equals("enable")){
						mPlayController.setPlayVoiceEnable(true, true);
					}else if(playMode.equals("disable")){
						mPlayController.setPlayVoiceEnable(false, true);
					}
				}	

			};
			mContext.registerReceiver(mVoiceCtrReceiver, new IntentFilter(ProductConstant.ACTION_VOICE_CTRL));
		}
		
		private TimeLogSaveListener mLogSaveListener = new TimeLogSaveListener() {
		
			@Override
			public void onSave(TimeLog log) {
				if (null != mAIUIAgent) {
					JSONObject jsonLog = log.toJson();
		
					AIUIMessage logMsg = new AIUIMessage(AIUIConstant.CMD_SEND_LOG, 0, 0, 
							jsonLog.toString(), null);
					mAIUIAgent.sendMessage(logMsg);
		
					DebugLog.LogD(TAG, "saveTimeLog");
				}
			}
		};
	
		//AIUI事件处理方法
		@Override
		public void onEvent(AIUIEvent event) {

			switch (event.eventType) {
			case AIUIConstant.EVENT_BIND_SUCESS:
			{
				DebugLog.LogD(TAG, "EVENT_BIND_SUCESS");

				mPlayController.playTone("", START_SUCCESS);
					            
				// 关闭唤醒方向指示灯
	            DevBoardControlUtil.sleepLight();

	            // 设置WIFI指示灯
	            DevBoardControlUtil.wifiStateLight(NetworkUtil.isNetworkAvailable(mContext));
	            
				buildGrammar();
			} break;

			case AIUIConstant.EVENT_WAKEUP: 
			{
				DebugLog.LogD(TAG, "EVENT_WAKEUP");
				
				processWakeup(event);
			} break;

			case AIUIConstant.EVENT_SLEEP: 
			{
				DebugLog.LogD(TAG, "EVENT_SLEEP");
				
				mIsWakeUp = false;
				
				if(mSleepErrorCode != 0){
					mPlayController.justTTS("", getErrorTip(mSleepErrorCode), false, new Runnable() {
						
						@Override
						public void run() {
							DevBoardControlUtil.sleepLight();
						}
					});
					mSleepErrorCode = 0;
				}else{
					// 正在播放音乐且为自动休眠，直接熄灯
					if ((mPlayController.isCurrentPlayMusic() || mPlayController.isCurrentTTS()) && event.arg1 ==0) {
						DevBoardControlUtil.sleepLight();
					} else {
					    if(event.arg1 == 0)
					        sayGoodBye("", true);
					}
				}
			} break;

			case AIUIConstant.EVENT_RESULT: 
			{
				DebugLog.LogD(TAG, "EVENT_RESULT");
				
				if (!mIsWakeUp) {
					break;
				}

				processResult(event);
			} break;

			case AIUIConstant.EVENT_VAD:
			{

			} break;

			case AIUIConstant.EVENT_ERROR: 
			{
				int errorCode = event.arg1;
				processError(errorCode);
			} break;

			case AIUIConstant.EVENT_STATE: 
			{
				int serviceState = event.arg1;
				if (AIUIConstant.STATE_IDLE == serviceState) {
					DebugLog.LogD(TAG, "STATE_IDLE");

					DevBoardControlUtil.appidErrorLight(false);
				}
			} break;
			
			case AIUIConstant.EVENT_CMD_RETURN: {
				processCmdReturnEvent(event);
			} break;
			
			default:
				break;
			}
		}


		private void processWakeup(AIUIEvent event) {
			mPlayController.onMusicCommand("", InsType.PAUSE);
			mPlayController.stopTTS();
		
			if (!mIsWakeUp && ConfigUtil.isSaveAppTimeLog()) {
				AppTimeLogger.onRealWakeup();
				DebugLog.LogD(TAG, "makeWakeDir");
			}
		
			mIsWakeUp = true;
		
			try {
				JSONObject wakeInfo = new JSONObject(event.info);
				
				int wakeAngle = wakeInfo.getInt("angle");
		
				DebugLog.LogD(TAG, "wakeAngle=" + wakeAngle);
				
				//wikeAngle为-1时为内部唤醒，其他为手动唤醒
				if(wakeAngle != -1){
				    DevBoardControlUtil.wakeUpLight(wakeAngle);
				    mPlayController.playTone("", WAKE_UP_TONES[new Random().nextInt(WAKE_UP_TONES.length)]);
				    mPreWakeUpJson = wakeInfo;
				}else{
				    if(mPreWakeUpJson != null){
	                    DevBoardControlUtil.wakeUpLight(mPreWakeUpJson.getInt("angle"));
	                }
				}
				
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			
		}
		
		private void resetWakeup(boolean resetAIUI) {
			if (null != mAIUIAgent) {
				if (resetAIUI) {
					AIUIMessage resetMsg = new AIUIMessage(
							AIUIConstant.CMD_RESET, 0, 0, "", null);
					mAIUIAgent.sendMessage(resetMsg);
		
					DebugLog.LogD(TAG, "reset AIUI");
				} else {
					// 重置唤醒状态
					AIUIMessage resetWakeupMsg = new AIUIMessage(
							AIUIConstant.CMD_RESET_WAKEUP, 0, 0, "", null);
					mAIUIAgent.sendMessage(resetWakeupMsg);
		
					DebugLog.LogD(TAG, "reset Wakeup");
				}
			}
		}
		
		private void processCmdReturnEvent(AIUIEvent event) {
			switch (event.arg1) {
		
			case AIUIConstant.CMD_BUILD_GRAMMAR: {
				Log.d(TAG, "构建语法成功");
			} break;
		
			default:
				break;
			}
		}
		
		private void buildGrammar() {
			String grammar = FileUtil.readAssetsFile(mContext, GRAMMAR_FILE_PATH);
			AIUIMessage buildGrammar = new AIUIMessage(AIUIConstant.CMD_BUILD_GRAMMAR, 
					0, 0, grammar, null);
		
			mAIUIAgent.sendMessage(buildGrammar);
			Log.d(TAG,"sendMessage start");
		}


		/**
		 * 出错处理函数。
		 * 
		 * @param errorCode 错误码
		 */
		private void processError(final int errorCode) {
			DebugLog.LogD(TAG, "AIUI error=" + errorCode);

			// 错误提示
			switch (errorCode) {
				case AIUIErrorCode.MSP_ERROR_TIME_OUT:
				case AIUIErrorCode.MSP_ERROR_NO_RESPONSE_DATA:  // 结果超时

				case AIUIErrorCode.MSP_ERROR_LMOD_RUNTIME_EXCEPTION:		// 16005，需要重启AIUI会话									
				case AIUIErrorCode.MSP_ERROR_NOT_FOUND:
				{
				    mSleepErrorCode = errorCode;
                    resetWakeup(false);
				}
				break;
				// appid校验不通过
				case AIUIErrorCode.MSP_ERROR_DB_INVALID_APPID:
				{
					DevBoardControlUtil.appidErrorLight(true);
					mPlayController.playTone("", TONE_WRONG_APPID);
				} break;
				
				case AIUIErrorCode.ERROR_SERVICE_BINDER_DIED:
				case AIUIErrorCode.ERROR_NO_NETWORK:
				{
					mPlayController.justTTS("",getErrorTip(errorCode) , false, new Runnable() {
						
						@Override
						public void run() {
							DevBoardControlUtil.sleepLight();
						}
					});
				}break;
				case AIUIErrorCode.MSP_ERROR_NLP_TIMEOUT:
				{
					mPlayController.playText("", "语义结果超时，请稍等一下！");
				}break;
				
				case -1:
				{
				    if(mIsWakeUp){
				        mPlayController.justTTS("", "内容开小差，换点别的吧！", "");
				    }
				} break;
			}
		}
		
		private String getErrorTip(int errorCode){
			switch (errorCode) {
			case AIUIErrorCode.MSP_ERROR_NOT_FOUND:
				return "场景参数设置出错！";
			case AIUIErrorCode.ERROR_SERVICE_BINDER_DIED:
				return "AIUI服务已断开！";
			case  AIUIErrorCode.ERROR_NO_NETWORK:
			    return "网络未连接，请连接网络！";
			default:
				return "网络有点问题我去休息了，请稍后再试！";
			}
		}


		private void processResult(AIUIEvent event) {
				long posRsltOnArrival = System.currentTimeMillis();
		
				try {
					JSONObject bizParamJson = new JSONObject(event.info);
					JSONObject data = bizParamJson.getJSONArray("data").getJSONObject(0);
					JSONObject params = data.getJSONObject("params");
					JSONObject content = data.getJSONArray("content").getJSONObject(0);
		
					if (content.has("cnt_id")) {
						String cnt_id = content.getString("cnt_id");
						JSONObject cntJson = new JSONObject(new String(event.data.getByteArray(cnt_id), "utf-8"));
						String sub = params.optString("sub");
						JSONObject result = cntJson.getJSONObject("intent");
						long posRsltParseFinish = System.currentTimeMillis();
						
						if ("nlp".equals(sub)) {
							//在线语义结果
							mSemanticHandler.handleResult(result, event.data, params.toString(), posRsltOnArrival, posRsltParseFinish);	
						} else if ("asr".equals(sub)) {
							// 处理离线语法结果
							mAsrHandler.handleResult(result);
						}
//						后处理结果
//						else if("tpp".equals(sub)) {
//							//if 后处理结果有效， 发送后处理结果确认消息，以重置语义超时计时，延长交互时间
//							//mAIUIAgent.sendMessage(new AIUIMessage(AIUIConstant.CMD_TPP_RESULT_ACK, 0, 0, "", null));
//
//						}
					}
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		
		private void sayGoodBye(String uuid, boolean isAutoSleep){
		    mPlayController.onMusicCommand("", InsType.PAUSE);
            mPlayController.stopTTS();
            
            if(!isAutoSleep){
                directSleep(uuid);
            }else{
                keepWakeOrSleep(uuid);
            }
	        
		}
		
		private void directSleep(String uuid){
		    mPlayController.playTone(uuid, XIAOAI_GOODBYE_NWZL, new Runnable() {
                
                @Override
                public void run() {
                    mKeepWakeUp = false;
                    resetWakeup(false);
                    gotoSleep("");
                }
            });
		}
		
		private void keepWakeOrSleep(String uuid){
		    mPlayController.playTone(uuid, XIAOAI_GOODBYE_NWZL, new Runnable() {
                @Override
                public void run() {
                    if(mPreWakeUpJson != null){
                        try {
                            sendWakeUpMessage(mPreWakeUpJson.getInt("beam"));
                            mKeepWakeUp = false;
                            mDelaySleepHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if(!mKeepWakeUp){
                                        resetWakeup(false);
                                        gotoSleep("");
                                    }
                                    mKeepWakeUp = false;
                                }
                            }, Delay_SLEEP_TIME);
                        } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            });
		}
		/**
		 * 休眠操作。
		 * 
		 * @param uuid
		 */
		private void gotoSleep(final String uuid) {
			DebugLog.LogD(TAG, "gotoSleep");
			
			mPlayController.stopPlayControl();
		
			AppTimeLogger.onSleep(uuid);
	        
            DevBoardControlUtil.sleepLight();
			
		}

		private void sendWakeUpMessage(int beam){
		    AIUIMessage resetWakeupMsg = new AIUIMessage(
                    AIUIConstant.CMD_WAKEUP, beam, 0, "", null);
            mAIUIAgent.sendMessage(resetWakeupMsg);
		}

		private void unregisterReceiver(){
			mContext.unregisterReceiver(mSleepReceiver);
			mContext.unregisterReceiver(mVoiceCtrReceiver);
		}


		public void destroy(){
			unregisterReceiver();
			mSemanticHandler.destroy();
			mAsrHandler.destroy();
			mAIUIAgent = null;
		}
		
		//PlayListenner OnError
		@Override
		public void onError(PalyControllerItem playItem, final int errorCode) {
			DebugLog.LogD(TAG, "TTS Error. ErrorCode=" + errorCode);
			
			processError(errorCode);
		}
}
