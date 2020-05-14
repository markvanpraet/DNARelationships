package com.markvanpraet.dnarelationships

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.ExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.NumberFormat

import java.util.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private val cmMinValue: Double = 1.0
    private val cmMaxValue: Double = 3720.0
    private val cmPercentConv: Double = 7460.0

    private val decFormat = NumberFormat.getNumberInstance()

    private val groupColumn: HashMap<String, Int> = hashMapOf(
        "AA" to 0,
        "A" to 1,
        "B" to 2,
        "C" to 3,
        "D" to 4,
        "E" to 5,
        "F" to 6,
        "G" to 7,
        "H" to 8,
        "I" to 9,
        "J+" to 10
    )

    private lateinit var ranges: ArrayList<Ranges>
    private lateinit var likelihoods: ArrayList<Likelihoods>
    private lateinit var groupings: ArrayList<Groupings>

    private var expandableListView: ExpandableListView? = null
    private var adapter: ExpandableListAdapter? = null
    private var titleList: List<Double>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //
        // calculation logic (load results into hashmap<%, relationships<array>)
        //
        ranges = loadRanges()
        likelihoods = loadLikelihoods()
        groupings = loadGroupings()

        decFormat.maximumFractionDigits = 2

        // disable button unless values entered
        cmValueTxt.addTextChangedListener(object: TextWatcher {
            override fun onTextChanged(s:CharSequence, start:Int, before:Int, count:Int) {
                findRelationshipsBtn.isEnabled = s.toString().trim { it <= ' ' }.isNotEmpty()
            }
            override fun beforeTextChanged(s:CharSequence, start:Int, count:Int,
                                           after:Int) {
            }
            override fun afterTextChanged(s: Editable) {
            }
        })
        //
        // main logic for processing entered values
        findRelationshipsBtn.setOnClickListener {

            // hide soft keypad once the button is pushed
            val immHandle = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            immHandle.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)

            var centimorgans: Double

            // assess whether cm or % selected as UOM
            if(cmRadBtn.isChecked) {
                centimorgans = decFormat.parse(cmValueTxt.text.toString()).toDouble()
                // constrain to allowed values
                if (centimorgans > cmMaxValue) {
                    centimorgans = cmMaxValue
                    cmValueTxt.setText(centimorgans.toString())
                    Toast.makeText(
                        applicationContext,
                        "Maximum centimorgan value of $cmMaxValue has been substituted",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (centimorgans < cmMinValue) {
                    centimorgans = cmMinValue
                    cmValueTxt.setText(centimorgans.toString())
                    Toast.makeText(
                        applicationContext,
                        "Minimum centimorgan value of $cmMinValue has been substituted",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            // percentage entered
            else {
                var percent = decFormat.parse(cmValueTxt.text.toString()).toDouble()
                if (percent > 100) {
                    percent = 100.0
                    cmValueTxt.setText(percent.toString())
                    Toast.makeText(
                        applicationContext,
                        "Maximum percentage value of 100 has been substituted",
                        Toast.LENGTH_LONG
                    ).show()
                }
                centimorgans = cmPercentConv / 100 * percent     // convert percent to centimorgans
                Toast.makeText(
                    applicationContext,
                    "Percentage value equates to ${decFormat.format(centimorgans)} centimorgans",
                    Toast.LENGTH_LONG
                ).show()
            }


// retrieve grouping data for relationships in scope based on entered value
            val groupData = getGroupData(centimorgans)

            val groupPercentages = HashMap<String, Double>()
            val relationshipPercentagesGroups = HashMap<Double, MutableList<String>>()

            for (rel in groupData) {
                val probability = getProbability(centimorgans, rel.group)
                groupPercentages[rel.relationship] = probability
                var tmpArray: ArrayList<String>
                tmpArray = if (relationshipPercentagesGroups[probability].isNullOrEmpty()) {
                    ArrayList<String>()
                } else {
                    relationshipPercentagesGroups[probability] as ArrayList<String>
                }
                tmpArray.add(rel.relationshipFull)
                relationshipPercentagesGroups[probability] = tmpArray
            }

            expandableListView = findViewById(R.id.expandableListView)
            if (expandableListView != null) {
                titleList = ArrayList(relationshipPercentagesGroups.keys.sortedDescending())
                adapter =
                    CustomExpandableListAdapter(
                        this,
                        titleList as ArrayList<Double>,
                        relationshipPercentagesGroups
                    )
                expandableListView!!.setAdapter(adapter)


//                expandableListView!!.setOnGroupExpandListener { groupPosition ->
//                    Toast.makeText(
//                        applicationContext,
//                        (titleList as ArrayList<Double>)[groupPosition].toString() + " List Expanded.",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }

//                expandableListView!!.setOnGroupCollapseListener { groupPosition ->
//                    Toast.makeText(
//                        applicationContext,
//                        (titleList as ArrayList<Double>)[groupPosition].toString() + " List Collapsed.",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }

//                expandableListView!!.setOnChildClickListener { parent, v, groupPosition, childPosition, id ->
//                    Toast.makeText(
//                        applicationContext,
//                        "Clicked: " + (titleList as ArrayList<Double>)[groupPosition] + " -> " + relationshipPercentagesGroups[(titleList as ArrayList<Double>)[groupPosition]]!!.get(
//                            childPosition
//                        ),
//                        Toast.LENGTH_SHORT
//                    ).show()
//                    false
//                }
            }
        }
    }

    private fun getGroupData(cm: Double): ArrayList<Groupings> {

        val groupData = ArrayList<Groupings>()
        for (r in ranges) {
            if (cm >= r.fromRange && cm <= r.toRange) {
                groupData.add(groupings.first { it.relationship == r.relationship })
            }
        }
        return groupData
    }

    private fun getProbability(cm: Double, group: String): Double {
        // get adjacent likelihoods
        val startEnd = getAdjacentLikelihoods(cm)
        // perform calculation
        // get likelihood for group in start/end (wrapping) probability
        val startProb = likelihoods.first { it.cmBasis == startEnd[0] }
        val startProbVal: Double = startProb.likelihood.get(index = groupColumn[group]!!)
        val endProb = likelihoods.first { it.cmBasis == startEnd[1] }
        val endProbVal: Double = endProb.likelihood.get(index = groupColumn[group]!!)

        return ((startEnd[1] - cm) * startProbVal + (cm - startEnd[0]) * endProbVal) / (startEnd[1] - startEnd[0])
    }

    private fun loadRanges(): ArrayList<Ranges> {
        val ranges = ArrayList<Ranges>()
        val inputStream: InputStream = resources.openRawResource(R.raw.ranges)
        val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))

        reader.readLines().forEach {
            val items = it.split(",")
            ranges.add(Ranges(items[2], items[0].toInt(), items[1].toInt()))
        }
        return ranges
    }

    private fun loadGroupings(): ArrayList<Groupings> {
        val groups = ArrayList<Groupings>()
        val inputStream: InputStream = resources.openRawResource(R.raw.groupings)
        val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))

        reader.readLines().forEach {
            val items = it.split(",")
            groups.add(Groupings(items[0], items[1], items[2].toInt(), items[3], items[4]))
        }
        return groups
    }

    private fun loadLikelihoods(): ArrayList<Likelihoods> {
        val weightings = ArrayList<Likelihoods>()
        val inputStream: InputStream = resources.openRawResource(R.raw.likelihoods)
        val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))

        var cmVal: Double = -1.0
        val cmArr = arrayListOf<Double>()

        reader.readLines().forEach {
            val items = it.split(",")
            for ((index, item) in items.withIndex()) {
                if (index == 0) {
                    cmVal = decFormat.parse(item)!!.toDouble()
                    cmArr.clear()
                } else {
                    cmArr.add(decFormat.parse(item)!!.toDouble())
                    if (index == items.size - 1) {
                        weightings.add(Likelihoods(cmVal, cmArr.toDoubleArray()))
                    }
                }
            }
        }
        return weightings
    }

    private fun getAdjacentLikelihoods(inCM: Double): DoubleArray {
        val firstLast = DoubleArray(2)    // ind 0 = first   ind 1 = last

        firstLast[0] = likelihoods[0].cmBasis

        var diff = abs(inCM - firstLast[0])
        for (i in 0 until likelihoods.size) {
            val newDiff = abs(inCM - likelihoods[i].cmBasis)
            if (newDiff <= diff) {
                diff = newDiff
                firstLast[0] = 0.0
                firstLast[1] = 0.0
                if (inCM < likelihoods[i].cmBasis) {
                    firstLast[0] = likelihoods[i - 1].cmBasis
                    firstLast[1] = likelihoods[i].cmBasis
                } else if (inCM > likelihoods[i].cmBasis) {
                    firstLast[0] = likelihoods[i].cmBasis
                    if (i == likelihoods.size - 1) firstLast[1] = likelihoods[i].cmBasis
                    else firstLast[1] = likelihoods[i + 1].cmBasis
                }
            }
        }
        return firstLast
    }
}