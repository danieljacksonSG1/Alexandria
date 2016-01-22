package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.util.Patterns;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.DownloadImage;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private EditText ean;
    private final int LOADER_ID = 1;
    private View rootView;
    private final String EAN_CONTENT="eanContent";
    private static final String SCAN_FORMAT = "scanFormat";
    private static final String SCAN_CONTENTS = "scanContents";
    public static final String TAG = AddBook.class.getSimpleName();

    private String mScanFormat = "Format:";
    private String mScanContents = "Contents:";

    String mCurrentPhotoPath;

    ImageView mBookBarcode;
    Bitmap myBitmap;



    public AddBook(){
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(ean!=null) {
            outState.putString(EAN_CONTENT, ean.getText().toString());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_add_book, container, false);
        ean = (EditText) rootView.findViewById(R.id.ean);
        mBookBarcode = (ImageView) rootView.findViewById(R.id.bookBarcode);

        myBitmap = BitmapFactory.decodeResource(getActivity().getResources(),R.drawable.eantest);
        mBookBarcode.setImageBitmap(myBitmap);

        ean.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //no need
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                String ean =s.toString();
                //catch isbn10 numbers
                if(ean.length()==10 && !ean.startsWith("978")){
                    ean="978"+ean;
                }
                if(ean.length()<13){
                    clearFields();
                    return;
                }
                // Passes the ISBN into the method and starts the AddBook intent
                startAddBookIntent(ean);

            }
        });

        rootView.findViewById(R.id.scan_button).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                // This is the callback method that the system will invoke when your button is
                // clicked. You might do this by launching another app or by including the
                //functionality directly in this app.
                // Hint: Use a Try/Catch block to handle the Intent dispatch gracefully, if you
                // are using an external app.
                //when you're done, remove the toast below.

                // Take a picture of the image and store into the imageView
                // http://developer.android.com/training/camera/photobasics.html

                takePicture();


            }
        });

        rootView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ean.setText("");
            }
        });

        rootView.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, ean.getText().toString());
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);
                ean.setText("");
            }
        });

        if(savedInstanceState!=null){
            ean.setText(savedInstanceState.getString(EAN_CONTENT));
            ean.setHint("");
        }

        return rootView;
    }

    private void takePicture()
    {
        final int REQUEST_IMAGE_CAPTURE = 1;
        Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Check if we have a camera app installed
        if (takePicture.resolveActivity(getActivity().getPackageManager()) != null)
        {
            Log.i(TAG, "Starting image capture");

            File photoFile = createImageFile();
            Log.i(TAG, "Saving photo to: " + photoFile.getAbsolutePath());

            // Keep track of the temporary file save location
            mCurrentPhotoPath = photoFile.getAbsolutePath();

            if (photoFile != null)
            {
                Log.i(TAG, "Saving file...");
                takePicture.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                startActivityForResult(takePicture, REQUEST_IMAGE_CAPTURE);
            }


        }
        else
        {
            Toast.makeText(getActivity(), "No camera app installed", Toast.LENGTH_LONG).show();
        }
    }

    // Get the picture from the camera
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath);
        mBookBarcode.setImageBitmap(bitmap);

        // Now decode the bitmap
        decodeBarcode(bitmap);


    }

    // Takes a bitmap as an arg then decodes the ISBN
    public void decodeBarcode(Bitmap image)
    {
        Log.i(TAG, "Decoding image bitmap");

        // Decode the barcode
        // https://search-codelabs.appspot.com/codelabs/bar-codes#6
        BarcodeDetector detector = new BarcodeDetector.Builder(getActivity())
                .setBarcodeFormats(Barcode.EAN_13 | Barcode.QR_CODE | Barcode.EAN_8) // Check EAN 8, 13, and QR
                .build();
        if (!detector.isOperational())
        {
            Toast.makeText(getActivity(), "Could not setup the detector", Toast.LENGTH_LONG).show();
        }
        else
        {
            Log.i(TAG, "Detector is operational");

            Frame frame = new Frame.Builder().setBitmap(image).build();
            SparseArray<Barcode> barcodes = detector.detect(frame);

            try
            {
                Barcode thisCode = barcodes.valueAt(0);
                Log.i(TAG, "The ISBN is: " + thisCode.rawValue);
                // Once we have the barcode, pass the ISBN into the addBook method
                startAddBookIntent(thisCode.rawValue);
            }
            catch (ArrayIndexOutOfBoundsException e)
            {
                Log.i(TAG, "Error getting barcode");
            }


        }

    }

    // Takes the 13 digit ISBN as an arg
    private void startAddBookIntent(String ean)
    {
        try
        {
            //Once we have an ISBN, start a book intent
            Intent bookIntent = new Intent(getActivity(), BookService.class);
            bookIntent.putExtra(BookService.EAN, ean);
            bookIntent.setAction(BookService.FETCH_BOOK);
            getActivity().startService(bookIntent);
            AddBook.this.restartLoader();
        }
        catch(NullPointerException e)
        {
            Log.i(TAG, "Error parsing ISBN: " + e.getMessage());
        }
    }

    // http://developer.android.com/training/camera/photobasics.html
    private File createImageFile()
    {

        try
        {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = Environment.getExternalStorageDirectory();
            File image = File.createTempFile(imageFileName, ".jpg", storageDir);

            return image;

        }
        catch (IOException e)
        {
            Log.i(TAG, "Unable to write file" + e.getMessage());
            return null;
        }

    }





    private void restartLoader()
    {
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(ean.getText().length()==0){
            return null;
        }
        String eanStr= ean.getText().toString();
        if(eanStr.length()==10 && !eanStr.startsWith("978")){
            eanStr="978"+eanStr;
        }
        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(eanStr)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText(bookTitle);

        String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText(bookSubTitle);

        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));
        String[] authorsArr = authors.split(",");
        ((TextView) rootView.findViewById(R.id.authors)).setLines(authorsArr.length);
        ((TextView) rootView.findViewById(R.id.authors)).setText(authors.replace(",","\n"));
        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        if(Patterns.WEB_URL.matcher(imgUrl).matches()){
            new DownloadImage((ImageView) rootView.findViewById(R.id.bookCover)).execute(imgUrl);
            rootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);
        }

        String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
        ((TextView) rootView.findViewById(R.id.categories)).setText(categories);

        rootView.findViewById(R.id.save_button).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields(){
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.authors)).setText("");
        ((TextView) rootView.findViewById(R.id.categories)).setText("");
        rootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activity.setTitle(R.string.scan);
    }
}
