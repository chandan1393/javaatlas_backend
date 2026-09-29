package com.javaatlas.course;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_section")
public class CourseSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 160)
    private String title;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<Lecture> lectures = new ArrayList<>();

    protected CourseSection() {
    }

    CourseSection(Course course, String title, int sortOrder) {
        this.course = course;
        this.title = title;
        this.sortOrder = sortOrder;
    }

    void update(String title, int sortOrder) {
        this.title = title;
        this.sortOrder = sortOrder;
    }

    void add(Lecture lecture) {
        lecture.attach(this, lectures.size());
        lectures.add(lecture);
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public int getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public List<Lecture> getLectures() { return lectures; }
}
