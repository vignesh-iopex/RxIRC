package com.github.vignesh_iopex.ircapp.ui.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.github.vignesh_iopex.ircapp.R;
import com.github.vignesh_iopex.ircapp.services.IrcClientService;
import com.github.vignesh_iopex.rxirc.RxIrc;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

public class MainFragment extends Fragment {
  private static final String TAG = MainFragment.class.getSimpleName();
  private ListView listView;
  private ArrayAdapter<String> arrayAdapter;
  private EditText edtMessage;
  private List<String> incomingMessages;
  private Subscriber<String> subscriber = new Subscriber<String>() {
    @Override public void onCompleted() {
      Log.i(TAG, "Completed");
    }

    @Override public void onError(Throwable e) {
      e.printStackTrace();
    }

    @Override public void onNext(final String s) {
      incomingMessages.add(s);
      arrayAdapter.notifyDataSetChanged();
      listView.setSelection(listView.getCount() - 1);
    }
  };
  private PublishSubject<String> pushMessage = PublishSubject.create();

  @Nullable @Override public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    getActivity().getApplicationContext().bindService(new Intent(getActivity()
            .getApplicationContext(),
            IrcClientService.class),
        serviceConnection, Context.BIND_AUTO_CREATE);
    return inflater.inflate(R.layout.fragment_main, container, false);
  }

  public void hideKeyboard() {
    if (getActivity().getCurrentFocus() != null) {
      InputMethodManager inputMethodManager =
          (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus()
          .getWindowToken(), 0);
    }
  }

  @Override public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    listView = (ListView) view.findViewById(R.id.listview);
    incomingMessages = new ArrayList<>();
    arrayAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item, R.id.text,
        incomingMessages);
    edtMessage = (EditText) view.findViewById(R.id.edt_message);

    view.findViewById(R.id.btn_sendmessage).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        String message = edtMessage.getText().toString();
        pushMessage.onNext(message);
        subscriber.onNext("You: " + message);
        edtMessage.setText("");
        hideKeyboard();
      }
    });
    listView.setAdapter(arrayAdapter);
  }

  private void setChannelObservable(Observable<String> incoming) {
    incoming.observeOn(AndroidSchedulers.mainThread()).subscribe(subscriber);
  }

  private void onIrcConnected(final RxIrc rxIrc) {
    final View dialogView = getActivity().getLayoutInflater().inflate(R.layout.dialog_login, null);
    new AlertDialog.Builder(getActivity()).setView(dialogView)
        .setPositiveButton
            ("Login", new DialogInterface.OnClickListener() {
              @Override public void onClick(DialogInterface dialogInterface, int i) {
                EditText username = (EditText) dialogView.findViewById(R.id.username);
                EditText channel = (EditText) dialogView.findViewById(R.id.channelname);
                final String channelName = "#" + channel.getText().toString();
                Observable<String> observable = rxIrc.login(username.getText().toString(),
                    channelName);
                setChannelObservable(observable);
                rxIrc.readOutgoingMessageFrom(pushMessage.map(new Func1<String, String>() {
                  @Override public String call(String s) {
                    return "PRIVMSG " + channelName + " : " + s;
                  }
                }));
                hideKeyboard();
              }
            }).setCancelable(false).create().show();
    // ask for login
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
      IrcClientService.IrcBinder ircBinder = ((IrcClientService.IrcBinder) iBinder);
      new AsyncConnector().execute(ircBinder);
    }

    @Override public void onServiceDisconnected(ComponentName componentName) {

    }
  };

  private class AsyncConnector extends AsyncTask<IrcClientService.IrcBinder, Void, RxIrc> {
    @Override protected RxIrc doInBackground(IrcClientService.IrcBinder... ircBinders) {
      return ircBinders[0].connectAndGetIrc();
    }

    @Override protected void onPostExecute(RxIrc rxIrc) {
      super.onPostExecute(rxIrc);
      if (rxIrc != null) {
        onIrcConnected(rxIrc);
      }
    }
  }
}
