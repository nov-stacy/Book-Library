package ru.homebooks.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A non-modal receipt: the next book can be scanned immediately. */
final class BookNotice extends LinearLayout {
    BookNotice(Context context, String isbn, String title, boolean alreadySaved, boolean scanning) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.round(Color.WHITE, context));
        Ui.pad(this, 16);
        setElevation(Ui.dp(context, 2));
        setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        ImageView cover = new ImageView(context);
        cover.setImageDrawable(new Ui.Symbol("book", Ui.GREEN));
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.setBackground(Ui.surface(context,Ui.TINT,Ui.LINE,4));
        cover.setClipToOutline(true);
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(cover, new LayoutParams(Ui.dp(context, 42), Ui.dp(context, 60)));

        LinearLayout words = Ui.column(context);
        words.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 8), 0);
        addView(words, new LayoutParams(0, -2, 1));
        TextView label = Ui.text(context, alreadySaved ? "Уже в библиотеке" : "Книга на вашей полке", 14);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(Ui.GREEN);
        words.addView(label);
        Ui.gap(words, 4);
        TextView name = Ui.text(context, title, 13);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        Ui.gap(words, 5);
        words.addView(Ui.muted(context, scanning ? "Можно сканировать следующую" : alreadySaved ? "Повторную копию не добавили" : "Сохранена на этом телефоне", 13));

        ImageView check = new ImageView(context);
        check.setImageDrawable(new Ui.Symbol(alreadySaved ? "book" : "check", Ui.GREEN));
        check.setBackground(Ui.round(Ui.TINT, context));
        Ui.pad(check, 7);
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(check, new LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32)));

        if (Book.validId(isbn)) {
            LibraryApp app = (LibraryApp) context.getApplicationContext();
            app.io.execute(() -> {
                Book book=app.store.find(isbn);
                if(book==null)book=app.store.findByIsbn(isbn);
                String coverId=book==null?isbn:book.id;
                Bitmap bitmap = BitmapFactory.decodeFile(app.store.cover(coverId).getAbsolutePath());
                if (bitmap != null) post(() -> cover.setImageBitmap(bitmap));
            });
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setAlpha(0f);
        setTranslationY(Ui.dp(getContext(), 8));
        animate().alpha(1f).translationY(0f).setDuration(220).start();
    }

    @Override protected void onDetachedFromWindow() {
        animate().cancel();
        super.onDetachedFromWindow();
    }
}
