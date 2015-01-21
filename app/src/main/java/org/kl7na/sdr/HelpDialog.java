package org.kl7na.sdr;

/**
 * Created by KL7NA on 1/20/15.
 */

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.util.Linkify;
import android.widget.TextView;


public class HelpDialog extends AboutDialog {

    private static Context mContext = null; //was private static Context, Rob
    public HelpDialog(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.about);
        TextView tv = (TextView)findViewById(R.id.legal_text);
        tv = (TextView)findViewById(R.id.info_text);
        tv.setText("Version "+mContext.getString(R.string.version)+" "+ Html.fromHtml(readRawTextFile(R.raw.info)));
        tv.setLinkTextColor(Color.WHITE);
        Linkify.addLinks(tv, Linkify.ALL);
    }
}
