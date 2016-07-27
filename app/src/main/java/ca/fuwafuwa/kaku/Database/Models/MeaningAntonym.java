package ca.fuwafuwa.kaku.Database.Models;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Created by 0x1bad1d3a on 7/25/2016.
 */
@DatabaseTable
public class MeaningAntonym {

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(foreign = true)
    private Meaning fkMeaning;

    @DatabaseField
    private String antonym;

    public String getAntonym() {
        return antonym;
    }

    public void setAntonym(String antonym) {
        this.antonym = antonym;
    }
}