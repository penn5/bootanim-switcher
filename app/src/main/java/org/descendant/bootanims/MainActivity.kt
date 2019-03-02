package org.descendant.bootanims

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_main.view.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {

    /**
     * The [androidx.viewpager.widget.PagerAdapter] that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * androidx.fragment.app.FragmentStatePagerAdapter.
     */
    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null

    public fun play(view: View?) {
        // does nothing if null
        (view as? BootanimationView)?.restart()
        Handler().postDelayed({ (view as? BootanimationView)?.end() }, 10000)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        // Set up the ViewPager with the sections adapter.
        container.adapter = mSectionsPagerAdapter
        container.addOnPageChangeListener(mSectionsPagerAdapter!!)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        if (id == R.id.action_settings) {
            return true
        }

        return super.onOptionsItemSelected(item)
    }


    private val tag = "MainActivity"

    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm),
        ViewPager.OnPageChangeListener {

        private var selectedPage: Int = 0
        private var pages: ConcurrentHashMap<Int, PlaceholderFragment> = ConcurrentHashMap()
        override fun onPageScrollStateChanged(state: Int) {
            val tmpPage = selectedPage
            when (state) {
                ViewPager.SCROLL_STATE_DRAGGING -> pages[tmpPage]?.view?.bootanimationView?.pause()
                ViewPager.SCROLL_STATE_SETTLING -> pages[tmpPage]?.view?.bootanimationView?.pause()
                ViewPager.SCROLL_STATE_IDLE -> {
                    if (pages[tmpPage]?.view?.bootanimationView?.isStopped() != false) {
                        Log.e(tag, "restarting")
                        play(pages[tmpPage]?.view?.bootanimationView) // Acts how `resume` would if it existed
                    } else {
                        pages[tmpPage]?.view?.bootanimationView?.start()
                    }
                    Handler().postDelayed({ pages[tmpPage]?.view?.bootanimationView?.end() }, 10000)
                }

            }
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

        }

        override fun onPageSelected(position: Int) {
            if (selectedPage != position) {
                pages[selectedPage]?.view?.bootanimationView?.stop() // Clear the cache of it to save RAM
                selectedPage = position
                Log.w(tag, "page changed!!!")
                play(pages[position]?.view?.bootanimationView)
            } else {
                Log.w(tag, "same page")
                pages[position]?.view?.bootanimationView?.start()
                Handler().postDelayed({ pages[position]?.view?.bootanimationView?.end() }, 10000)
            }
        }


        override fun getItem(position: Int): Fragment {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1)
        }

        override fun getCount(): Int {
            // Show 3 total pages.
            return 3
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val ret = super.instantiateItem(container, position)
            pages[position] = ret as PlaceholderFragment
            return ret
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    class PlaceholderFragment : Fragment() {

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val rootView = inflater.inflate(R.layout.fragment_main, container, false)
            rootView.bootanimationView.setAnimation(File("/system/media/bootanimation.zip"))
            rootView.bootanimationView.loadFirstFrame()
            rootView.bootanimationView.invalidate()
            return rootView
        }

        companion object {
            /**
             * The fragment argument representing the section number for this
             * fragment.
             */
            private const val ARG_SECTION_NUMBER = "section_number"

            /**
             * Returns a new instance of this fragment for the given section
             * number.
             */
            fun newInstance(sectionNumber: Int): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                fragment.arguments = args
                return fragment
            }
        }
    }
}
