import pyaudio
import wave
import keyboard
import subprocess
import whisper

class VR_AR:
    def __init__(self):
        self.RESPEAKER_RATE = 16000
        self.RESPEAKER_CHANNELS = 1  # 1 CHANNEL FIRMWARE OR 6 CHANNEL FIRMWARE AS REQUIRED
        self.RESPEAKER_WIDTH = 2
        self.RESPEAKER_INDEX = 8  # RUN getDeviceInfo.py TO GET INDEX
        self.CHUNK = 1024
        self.flag = False
        self.idle_count = 0
        self.model = whisper.load_model("medium.en")

    def get_audio(self):
        aud = pyaudio.PyAudio()
        frames = []
        
        try:
            stream = aud.open(
                rate=self.RESPEAKER_RATE,
                format=aud.get_format_from_width(self.RESPEAKER_WIDTH),
                channels=self.RESPEAKER_CHANNELS,
                input=True,
                input_device_index=self.RESPEAKER_INDEX
            )
            
            print("* recording")
            self.flag = True

            while self.flag:
                data = stream.read(self.CHUNK, exception_on_overflow=False)
                frames.append(data)
                
                if keyboard.is_pressed('esc'):
                    print(" * Done recording")
                    stream.stop_stream()
                    stream.close()
                    aud.terminate()
                    wf = wave.open('sample.mp3', 'wb')
                    wf.setnchannels(self.RESPEAKER_CHANNELS)
                    wf.setsampwidth(aud.get_sample_size(aud.get_format_from_width(self.RESPEAKER_WIDTH)))
                    wf.setframerate(self.RESPEAKER_RATE)
                    wf.writeframes(b''.join(frames))
                    wf.close()
                    self.flag = False

        except Exception as err:
            print("ERROR:", err)
     
       
    def voice_to_text(self):
	    result = self.model.transcribe('sample.mp3')
	    return result["text"]
     	

if __name__ == "__main__":
    voice = VR_AR()
    voice.get_audio()
    voice.voice_to_text()
