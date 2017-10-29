/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ViewGroup;

import com.bluelinelabs.conductor.Conductor;
import com.bluelinelabs.conductor.Router;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.BottomNavigationController;
import com.nextcloud.talk.controllers.ServerSelectionController;
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider;
import com.nextcloud.talk.events.CertificateEvent;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.ssl.MagicTrustManager;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.requery.Persistable;
import io.requery.reactivex.ReactiveEntityStore;

@AutoInjector(NextcloudTalkApplication.class)
public final class MainActivity extends AppCompatActivity implements ActionBarProvider {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.controller_container)
    ViewGroup container;

    @Inject
    UserUtils userUtils;
    @Inject
    ReactiveEntityStore<Persistable> dataStore;

    @Inject
    EventBus eventBus;

    private Router router;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        router = Conductor.attachRouter(this, container, savedInstanceState);

        if (!router.hasRootController() && userUtils.anyUserExists()) {
            router.setRoot(RouterTransaction.with(new BottomNavigationController(R.menu.menu_navigation))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        } else if (!router.hasRootController()) {
            router.setRoot(RouterTransaction.with(new ServerSelectionController())
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        }
    }

    @Override
    public void onBackPressed() {
        if (!router.handleBack()) {
            super.onBackPressed();
        }
    }

    private void showCertificateDialog(X509Certificate cert, MagicTrustManager magicTrustManager) {
        DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG);
        String validFrom = formatter.format(cert.getNotBefore());
        String validUntil = formatter.format(cert.getNotAfter());

        String issuedBy = cert.getIssuerDN().toString();
        String issuedFor;

        try {
            if (cert.getSubjectAlternativeNames() != null) {
                StringBuilder stringBuilder = new StringBuilder();
                for (Object o : cert.getSubjectAlternativeNames()) {
                    List list = (List) o;
                    int type = (Integer) list.get(0);
                    if (type == 2) {
                        String name = (String) list.get(1);
                        stringBuilder.append("[").append(type).append("]").append(name).append(" ");
                    }
                }
                issuedFor = stringBuilder.toString();
            } else {
                issuedFor = cert.getSubjectDN().getName();
            }

            String dialogText = String.format(getResources()
                            .getString(R.string.nc_certificate_dialog_text), issuedBy, issuedFor,
                    validFrom, validUntil);

            new LovelyStandardDialog(this)
                    .setTopColorRes(R.color.darkRed)
                    .setNegativeButtonColorRes(R.color.darkRed)
                    .setPositiveButtonColorRes(R.color.colorPrimaryDark)
                    .setIcon(R.drawable.ic_security_white_24dp)
                    .setTitle(R.string.nc_certificate_dialog_title)
                    .setMessage(dialogText)
                    .setPositiveButton(R.string.nc_yes, v -> {
                        magicTrustManager.addCertInTrustStore(cert);
                    })
                    .setNegativeButton(R.string.nc_no, view1 -> {
                        router.setRoot(RouterTransaction.with(new
                                ServerSelectionController()));
                    })
                    .show();

        } catch (CertificateParsingException e) {
            e.printStackTrace();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(CertificateEvent event) {
        showCertificateDialog(event.getX509Certificate(), event.getMagicTrustManager());
    };

    @Override
    public void onStart() {
        super.onStart();
        eventBus.register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        eventBus.unregister(this);
    }
}
