package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener, TextToSpeech.OnInitListener {

    private enum Connected { False, Pending, True }

    private static final String OPEN_COMMAND = "1";
    private static final String CLOSE_COMMAND = "0";

    private String deviceAddress;
    private Context applicationContext;
    private SerialService service;

    private TextView connectionStatusText;
    private TextView deviceAddressText;
    private TextView gateStateText;
    private MaterialCardView overviewCard;
    private TextView availabilityTitleText;
    private TextView parkingSummaryText;
    private TextView assistantStatusText;
    private EditText assistantInput;
    private Button assistantSendButton;
    private Button assistantMicButton;
    private TextView assistantTranscriptText;
    private TextView assistantReplyText;
    private Button openGateButton;
    private Button closeGateButton;
    private MaterialCardView mapCard;
    private ParkingMapView parkingMapView;
    private TextView receiveText;

    private final ParkingState parkingState = new ParkingState(1);
    private final StringBuilder incomingLineBuffer = new StringBuilder();

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean ttsReady = false;

    private TextToSpeech textToSpeech;
    private GeminiParkingAssistant parkingAssistant;
    private ActivityResultLauncher<String> recordAudioPermissionLauncher;
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        applicationContext = requireContext().getApplicationContext();
        parkingAssistant = new GeminiParkingAssistant(BuildConfig.GEMINI_API_KEY);
        textToSpeech = new TextToSpeech(applicationContext, this);
        recordAudioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        launchSpeechRecognition();
                    } else if (isAdded()) {
                        Toast.makeText(getActivity(), R.string.microphone_permission_denied, Toast.LENGTH_SHORT).show();
                    }
                });
        speechRecognizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                        return;
                    }
                    List<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (results != null && !results.isEmpty()) {
                        String spokenText = results.get(0);
                        assistantInput.setText(spokenText);
                        submitAssistantPrompt(spokenText, true);
                    }
                });
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False) {
            disconnect();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (parkingAssistant != null) {
            parkingAssistant.shutdown();
        }
        applicationContext.stopService(new Intent(applicationContext, SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null) {
            service.attach(this);
        } else {
            getActivity().startService(new Intent(getActivity(), SerialService.class));
        }
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations()) {
            service.detach();
        }
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        connectionStatusText = view.findViewById(R.id.connection_status_text);
        deviceAddressText = view.findViewById(R.id.device_address_text);
        gateStateText = view.findViewById(R.id.gate_state_text);
        overviewCard = view.findViewById(R.id.overview_card);
        availabilityTitleText = view.findViewById(R.id.availability_title_text);
        parkingSummaryText = view.findViewById(R.id.parking_summary_text);
        assistantStatusText = view.findViewById(R.id.assistant_status_text);
        assistantInput = view.findViewById(R.id.assistant_input);
        assistantSendButton = view.findViewById(R.id.assistant_send_button);
        assistantMicButton = view.findViewById(R.id.assistant_mic_button);
        assistantTranscriptText = view.findViewById(R.id.assistant_transcript_text);
        assistantReplyText = view.findViewById(R.id.assistant_reply_text);
        openGateButton = view.findViewById(R.id.open_gate_button);
        closeGateButton = view.findViewById(R.id.close_gate_button);
        mapCard = view.findViewById(R.id.map_card);
        parkingMapView = view.findViewById(R.id.parking_map_view);
        receiveText = view.findViewById(R.id.receive_text);

        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        deviceAddressText.setText(getString(R.string.device_address_value, deviceAddress));
        assistantStatusText.setText(parkingAssistant.isGeminiConfigured()
                ? R.string.assistant_ready
                : R.string.assistant_local_mode);

        assistantSendButton.setOnClickListener(v -> submitAssistantPrompt(assistantInput.getText().toString(), true));
        assistantMicButton.setOnClickListener(v -> requestVoiceInput());
        assistantInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitAssistantPrompt(assistantInput.getText().toString(), true);
                return true;
            }
            return false;
        });
        openGateButton.setOnClickListener(v -> handleManualGateCommand(
                OPEN_COMMAND,
                ParkingState.GateState.OPEN,
                getString(R.string.manual_open_reply)));
        closeGateButton.setOnClickListener(v -> handleManualGateCommand(
                CLOSE_COMMAND,
                ParkingState.GateState.CLOSED,
                getString(R.string.manual_close_reply)));
        mapCard.setOnClickListener(v -> showExpandedMap());

        updateDashboard();
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.backgroundNotification)
                    .setChecked(service != null && service.areNotificationsEnabled());
        } else {
            menu.findItem(R.id.backgroundNotification).setChecked(true);
            menu.findItem(R.id.backgroundNotification).setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.backgroundNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!service.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                } else {
                    showNotificationSettings();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connected = Connected.Pending;
            updateDashboard();
            status("connecting...");
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        if (service != null) {
            service.disconnect();
        }
        updateDashboard();
    }

    private void handleManualGateCommand(String command, ParkingState.GateState gateState, String reply) {
        assistantReplyText.setText(reply);
        if (sendDeviceCommand(command)) {
            parkingState.setGateState(gateState);
            updateDashboard();
        }
    }

    private boolean sendDeviceCommand(String command) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), R.string.connect_first, Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            service.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            appendLog("APP -> " + command + '\n', R.color.colorSendText);
            return true;
        } catch (Exception e) {
            onSerialIoError(e);
            return false;
        }
    }

    private void submitAssistantPrompt(String userText, boolean speakReply) {
        String trimmed = userText == null ? "" : userText.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        assistantInput.getText().clear();
        assistantTranscriptText.setText(trimmed);
        assistantReplyText.setText(R.string.assistant_thinking);
        setAssistantBusy(true);
        ParkingState.Snapshot snapshot = parkingState.snapshot(connected == Connected.True);
        parkingAssistant.interpret(trimmed, snapshot, decision -> {
            if (!isAdded()) {
                return;
            }
            setAssistantBusy(false);
            assistantReplyText.setText(decision.getReply());
            if (OPEN_COMMAND.equals(decision.getCommand()) && sendDeviceCommand(decision.getCommand())) {
                parkingState.setGateState(ParkingState.GateState.OPEN);
            } else if (CLOSE_COMMAND.equals(decision.getCommand()) && sendDeviceCommand(decision.getCommand())) {
                parkingState.setGateState(ParkingState.GateState.CLOSED);
            }
            updateDashboard();
            if (speakReply) {
                speak(decision.getReply());
            }
        });
    }

    private void setAssistantBusy(boolean busy) {
        assistantSendButton.setEnabled(!busy);
        assistantMicButton.setEnabled(!busy);
        assistantSendButton.setText(busy ? R.string.assistant_thinking_button : R.string.assistant_send);
    }

    private void requestVoiceInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && getActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        launchSpeechRecognition();
    }

    private void launchSpeechRecognition() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.assistant_prompt_hint));
            speechRecognizerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getActivity(), R.string.speech_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDashboard() {
        if (getView() == null) {
            return;
        }
        ParkingState.Snapshot snapshot = parkingState.snapshot(connected == Connected.True);
        if (connected == Connected.Pending) {
            connectionStatusText.setText(R.string.connection_connecting);
            connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusPending));
        } else if (connected == Connected.True) {
            connectionStatusText.setText(R.string.connection_connected);
            connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusConnected));
        } else {
            connectionStatusText.setText(R.string.connection_disconnected);
            connectionStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusDisconnected));
        }

        gateStateText.setText(getString(R.string.gate_state_value, snapshot.getGateLabel()));
        availabilityTitleText.setText(snapshot.getHeadline());
        parkingSummaryText.setText(snapshot.getSummary());

        int overviewColor = ContextCompat.getColor(requireContext(), R.color.lotUnknown);
        if ("Parking Available".equals(snapshot.getHeadline())) {
            overviewColor = ContextCompat.getColor(requireContext(), R.color.lotAvailable);
        } else if ("Parking Full".equals(snapshot.getHeadline())) {
            overviewColor = ContextCompat.getColor(requireContext(), R.color.lotFull);
        }
        overviewCard.setCardBackgroundColor(overviewColor);
        parkingMapView.setSlotStates(snapshot.getSlots());
    }

    private void showExpandedMap() {
        ParkingMapView expandedMap = new ParkingMapView(requireContext());
        expandedMap.setExpanded(true);
        expandedMap.setSlotStates(parkingState.snapshot(connected == Connected.True).getSlots());

        FrameLayout container = new FrameLayout(requireContext());
        int padding = dp(10);
        container.setPadding(padding, padding, padding, padding);
        container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.cardSurface));
        container.addView(expandedMap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(560)));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.map_expanded_title)
                .setView(container)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void receive(ArrayDeque<byte[]> datas) {
        for (byte[] data : datas) {
            String message = new String(data, StandardCharsets.UTF_8);
            appendLog(message, R.color.colorRecieveText);
            processIncomingChunk(message);
        }
    }

    private void processIncomingChunk(String chunk) {
        incomingLineBuffer.append(chunk.replace("\r", ""));
        int newlineIndex;
        while ((newlineIndex = incomingLineBuffer.indexOf("\n")) >= 0) {
            String line = incomingLineBuffer.substring(0, newlineIndex).trim();
            incomingLineBuffer.delete(0, newlineIndex + 1);
            if (!line.isEmpty() && Esp32MessageParser.applyLine(line, parkingState)) {
                updateDashboard();
            }
        }
    }

    private void appendLog(String message, int colorResId) {
        SpannableStringBuilder builder = new SpannableStringBuilder(message);
        builder.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(requireContext(), colorResId)),
                0,
                builder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(builder);
    }

    private void status(String message) {
        appendLog(message + '\n', R.color.colorStatusText);
    }

    private void speak(String text) {
        if (ttsReady && textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "parking_ai_reply");
        }
    }

    private void showNotificationSettings() {
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", getActivity().getPackageName());
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (Arrays.equals(permissions, new String[]{Manifest.permission.POST_NOTIFICATIONS})
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !service.areNotificationsEnabled()) {
            showNotificationSettings();
        }
    }

    @Override
    public void onSerialConnect() {
        connected = Connected.True;
        status("connected");
        updateDashboard();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        connected = Connected.False;
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        connected = Connected.False;
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            int result = textToSpeech.setLanguage(Locale.getDefault());
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
        } else {
            ttsReady = false;
        }
    }
}
