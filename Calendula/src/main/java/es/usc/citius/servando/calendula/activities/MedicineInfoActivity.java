/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.enums.SnackbarType;

import java.util.List;

import es.usc.citius.servando.calendula.CalendulaActivity;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.adapters.MedInfoPageAdapter;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.DBRegistry;
import es.usc.citius.servando.calendula.drugdb.PrescriptionDBMgr;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.fragments.AlertListFragment;
import es.usc.citius.servando.calendula.fragments.MedInfoFragment;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.util.FragmentUtils;
import es.usc.citius.servando.calendula.util.IconUtils;

public class MedicineInfoActivity extends CalendulaActivity {

    private static final String TAG = "MedicineInfoActivity";
    AppBarLayout appBarLayout;
    CollapsingToolbarLayout toolbarLayout;
    TextView toolbarTitle;
    boolean appBarLayoutExpanded = true;
    Patient activePatient;
    Medicine medicine;
    PrescriptionDBMgr dbMgr;
    ImageView medIcon;
    int alertLevel = -1;
    private MedInfoPageAdapter mSectionsPagerAdapter;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private Handler handler;
    private boolean showAlerts = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.medicine_info, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.getItem(0).setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_pencil, R.color.white, 24, 2));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit:
                Intent intent = new Intent(this, MedicinesActivity.class);
                intent.putExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, medicine.getId());
                startActivity(intent);
                return true;
            case R.id.action_remove:
                showDeleteConfirmationDialog(medicine);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showDeleteConfirmationDialog(final Medicine m) {
        String message;
        if (!DB.schedules().findByMedicine(m).isEmpty()) {
            message = String.format(getString(R.string.remove_medicine_message_long), m.name());
        } else {
            message = String.format(getString(R.string.remove_medicine_message_short), m.name());
        }

        new MaterialStyledDialog.Builder(this)
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(this, CommunityMaterial.Icon.cmd_pill, R.color.white, 100))
                .setHeaderColor(R.color.android_red)
                .withDialogAnimation(true)
                .setTitle(getString(R.string.remove_medicine_dialog_title))
                .setDescription(message)
                .setCancelable(true)
                .setNeutralText(getString(R.string.dialog_no_option))
                .setPositiveText(getString(R.string.dialog_yes_option))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        DB.medicines().deleteCascade(m, true);
                        CalendulaApp.eventBus().post(PersistenceEvents.MEDICINE_EVENT);
                        finish();
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.cancel();
                    }
                })
                .show();
    }

    // Method called from the event bus
    @SuppressWarnings("unused")
    public void onEvent(Object evt) {
        if (evt instanceof PersistenceEvents.ModelCreateOrUpdateEvent) {
            Class<?> cls = ((PersistenceEvents.ModelCreateOrUpdateEvent) evt).clazz;
            Object model = ((PersistenceEvents.ModelCreateOrUpdateEvent) evt).model;
            if (cls.equals(Medicine.class) && model != null) {

                Medicine med = (Medicine) model;

                if (med.getId() == medicine.getId()) {

                    if (medicine.cn() == null && med.cn() != null) {
                        SnackbarManager.show(Snackbar.with(getApplicationContext())
                                        .type(SnackbarType.MULTI_LINE)
                                        .color(getResources().getColor(R.color.android_green_dark))
                                        .textColor(getResources().getColor(R.color.white))
                                        .duration(Snackbar.SnackbarDuration.LENGTH_SHORT)
                                        .text(R.string.message_med_linked_success)
                                , this);
                    }
                    ((MedInfoFragment) getViewPagerFragment(0)).notifyDataChange();
                    ((AlertListFragment) getViewPagerFragment(1)).notifyDataChange();
                    DB.medicines().refresh(medicine);
                    updateMedDetails();

                }
            }

        }
    }

    Fragment getViewPagerFragment(int position) {
        String tag = FragmentUtils.makeViewPagerFragmentName(R.id.container, position);
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_info);
        setupToolbar(null, Color.TRANSPARENT);
        setupStatusBar(Color.TRANSPARENT);
        handler = new Handler();
        activePatient = DB.patients().getActive(this);
        dbMgr = DBRegistry.instance().current();
        processIntent();

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new MedInfoPageAdapter(getSupportFragmentManager(), medicine);
        appBarLayout = (AppBarLayout) findViewById(R.id.appbar);
        toolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        toolbarTitle = (TextView) findViewById(R.id.toolbar_title);
        medIcon = (ImageView) findViewById(R.id.medicine_icon);

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.addOnPageChangeListener(getPageChangeListener());
        mViewPager.setOffscreenPageLimit(5);

        toolbarLayout.setContentScrimColor(activePatient.color());
        toolbarLayout.setBackgroundColor(activePatient.color());
        updateMedDetails();
        // Setup the tabLayout
        setupTabLayout();

        AppBarLayout.OnOffsetChangedListener mListener = new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                verticalOffset = Math.abs(verticalOffset);
                if (verticalOffset > 100) {
                    // collapse
                    toolbarTitle.animate().alpha(1);
                } else {
                    // expand
                    toolbarTitle.animate().alpha(0);
                }
            }
        };
        appBarLayout.addOnOffsetChangedListener(mListener);
        toolbarTitle.animate().alpha(0);

        if (showAlerts) {
            mViewPager.setCurrentItem(1);
        }

        CalendulaApp.eventBus().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CalendulaApp.eventBus().unregister(this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void updateMedDetails() {
        toolbarTitle.setText(getString(R.string.label_info_short) + " | " + medicine.name());
        ((TextView) findViewById(R.id.medicine_name)).setText(medicine.name());
        medIcon.setImageDrawable(IconUtils.icon(this, medicine.presentation().icon(), R.color.white));
    }

    private void processIntent() {

        long medId = getIntent() != null ? getIntent().getLongExtra("medicine_id", -1) : -1;
        showAlerts = getIntent() != null && getIntent().getBooleanExtra("show_alerts", false);

        if (medId != -1) {
            medicine = DB.medicines().findById(medId);
        }
        if (medicine == null) {
            Toast.makeText(MedicineInfoActivity.this, R.string.medicine_not_found_error, Toast.LENGTH_SHORT).show();
            finish();
        }

        List<PatientAlert> alerts = DB.alerts().findBy(PatientAlert.COLUMN_MEDICINE, medicine);
        for (PatientAlert a : alerts) {
            if (a.getLevel() > alertLevel) {
                alertLevel = a.getLevel();
            }
        }
    }

    private void setupTabLayout() {

        final TabLayout tabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
        tabLayout.setupWithViewPager(mViewPager);

        IIcon[] icons = new IIcon[]{
                CommunityMaterial.Icon.cmd_book_open,
                CommunityMaterial.Icon.cmd_message_alert
        };

        for (int i = 0; i < tabLayout.getTabCount(); i++) {

            Drawable icon = new IconicsDrawable(this)
                    .icon(icons[i])
                    .alpha(80)
                    .paddingDp(2)
                    .color(Color.WHITE)
                    .sizeDp(24);

            tabLayout.getTabAt(i).setIcon(icon);
        }
    }

    private ViewPager.OnPageChangeListener getPageChangeListener() {
        return new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (medicine != null) {
                    if (position == 0) {
                        toolbarTitle.setText(getString(R.string.label_info_short) + " | " + medicine.name());
                    } else if (position == 1) {
                        toolbarTitle.setText(getString(R.string.label_alerts_short) + " | " + medicine.name());
                    }

                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        };
    }

}