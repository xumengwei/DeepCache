package edu.pku.sei.cnncache;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import edu.pku.sei.cnncache.benchmark.BenchmarkActivity;
import edu.pku.sei.cnncache.classifier.ClassifierActivity;

/**
 * Created by echo on 25/05/2018.
 */

public class MainActivity extends Activity implements View.OnClickListener {
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		findViewById(R.id.benchmark_act).setOnClickListener(this);
		findViewById(R.id.classifier_act).setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.benchmark_act) {
			Intent intent = new Intent(this, BenchmarkActivity.class);
			startActivity(intent);
		}
		if (view.getId() == R.id.classifier_act) {
			Intent intent = new Intent(this, ClassifierActivity.class);
			startActivity(intent);
		}
	}
}
